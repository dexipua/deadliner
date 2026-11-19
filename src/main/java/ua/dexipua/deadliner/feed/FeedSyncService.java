package ua.dexipua.deadliner.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.DeadlineStatus;
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Connects a user's Moodle feed and keeps their deadlines in sync with it. */
@Service
public class FeedSyncService {

    private static final Logger log = LoggerFactory.getLogger(FeedSyncService.class);

    public sealed interface ConnectResult {
        record Connected(int count, Optional<Deadline> nearest) implements ConnectResult {
        }

        record InvalidUrl() implements ConnectResult {
        }

        record FeedUnreachable() implements ConnectResult {
        }
    }

    public sealed interface SyncOutcome {
        record Ok(int added, int updated, int gone) implements SyncOutcome {
        }

        /** The link itself looks broken (bad token, 4xx). */
        record Failed(int consecutiveFailures) implements SyncOutcome {
        }

        /** Moodle is down or slow; says nothing about the link, so it does not count toward the warning. */
        record Unavailable() implements SyncOutcome {
        }
    }

    private final FeedFetcher fetcher;
    private final IcalParser parser = new IcalParser();
    private final FeedUrlCipher cipher;
    private final AppUserRepository users;
    private final CalendarFeedRepository feeds;
    private final DeadlineRepository deadlines;
    private final ReminderSettings reminderSettings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public FeedSyncService(FeedFetcher fetcher, FeedUrlCipher cipher, AppUserRepository users,
                           CalendarFeedRepository feeds, DeadlineRepository deadlines,
                           ReminderSettings reminderSettings, TransactionTemplate tx, Clock clock) {
        this.fetcher = fetcher;
        this.cipher = cipher;
        this.users = users;
        this.feeds = feeds;
        this.deadlines = deadlines;
        this.reminderSettings = reminderSettings;
        this.tx = tx;
        this.clock = clock;
    }

    public ConnectResult connect(long telegramId, String rawUrl) {
        Optional<URI> url = FeedUrlValidator.normalize(rawUrl);
        if (url.isEmpty()) {
            return new ConnectResult.InvalidUrl();
        }
        List<ParsedDeadline> parsed;
        try {
            parsed = download(url.get());
        } catch (IOException | InvalidFeedException e) {
            log.info("Feed check failed for a new connection: {}", e.getMessage());
            return new ConnectResult.FeedUnreachable();
        }
        Instant now = clock.instant();
        return tx.execute(status -> {
            AppUser user = users.findByTelegramId(telegramId)
                    .orElseGet(() -> users.save(new AppUser(telegramId, now)));
            user.setActive(true);
            byte[] encrypted = cipher.encrypt(url.get().toString());
            CalendarFeed feed = feeds.findByUserId(user.getId())
                    .orElseGet(() -> new CalendarFeed(user.getId(), encrypted));
            feed.setUrlEncrypted(encrypted);
            feed.recordSuccess(now);
            feeds.save(feed);
            reminderSettings.createDefaultsIfMissing(user.getId());
            apply(user.getId(), parsed, now);

            List<Deadline> upcoming = deadlines.findByUserId(user.getId()).stream()
                    .filter(d -> d.getStatus() == DeadlineStatus.OPEN && d.getDueAt().isAfter(now))
                    .sorted(Comparator.comparing(Deadline::getDueAt))
                    .toList();
            return new ConnectResult.Connected(upcoming.size(), upcoming.stream().findFirst());
        });
    }

    public SyncOutcome sync(long feedId, Instant now) {
        CalendarFeed feed = feeds.findById(feedId).orElseThrow();
        try {
            List<ParsedDeadline> parsed = download(URI.create(cipher.decrypt(feed.getUrlEncrypted())));
            return tx.execute(status -> {
                SyncOutcome.Ok outcome = apply(feed.getUserId(), parsed, now);
                feed.recordSuccess(now);
                feeds.save(feed);
                return outcome;
            });
        } catch (FeedRejectedException | InvalidFeedException e) {
            // never log the URL: it contains the user's token
            feed.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
            feeds.save(feed);
            return new SyncOutcome.Failed(feed.getConsecutiveFailures());
        } catch (IOException e) {
            feed.recordUnavailable("Moodle unavailable: " + e.getMessage());
            feeds.save(feed);
            return new SyncOutcome.Unavailable();
        }
    }

    private List<ParsedDeadline> download(URI url) throws IOException, InvalidFeedException {
        try (InputStream body = fetcher.fetch(url)) {
            return parser.parse(body);
        }
    }

    private SyncOutcome.Ok apply(long userId, List<ParsedDeadline> parsed, Instant now) {
        Map<String, Deadline> existing = deadlines.findByUserId(userId).stream()
                .collect(Collectors.toMap(Deadline::getExternalUid, Function.identity()));
        Set<String> seen = new HashSet<>();
        int added = 0;
        int updated = 0;
        int gone = 0;
        for (ParsedDeadline p : parsed) {
            seen.add(p.uid());
            Deadline d = existing.get(p.uid());
            if (d == null) {
                d = new Deadline(userId, p.uid());
                copy(p, d);
                deadlines.save(d);
                added++;
            } else if (changed(p, d)) {
                copy(p, d);
                if (d.getStatus() == DeadlineStatus.GONE) {
                    d.setStatus(DeadlineStatus.OPEN);
                }
                updated++;
            }
        }
        for (Deadline d : existing.values()) {
            // past items drop out of Moodle's export window all the time; keep them for stats
            if (!seen.contains(d.getExternalUid()) && d.getStatus() == DeadlineStatus.OPEN
                    && d.getDueAt().isAfter(now)) {
                d.setStatus(DeadlineStatus.GONE);
                gone++;
            }
        }
        return new SyncOutcome.Ok(added, updated, gone);
    }

    private static boolean changed(ParsedDeadline p, Deadline d) {
        return d.getStatus() == DeadlineStatus.GONE
                || !Objects.equals(p.title(), d.getTitle())
                || !Objects.equals(p.courseName(), d.getCourseName())
                || !Objects.equals(p.dueAt(), d.getDueAt())
                || !Objects.equals(p.url(), d.getUrl());
    }

    private static void copy(ParsedDeadline p, Deadline d) {
        d.setTitle(p.title());
        d.setCourseName(p.courseName());
        d.setDueAt(p.dueAt());
        d.setUrl(p.url());
    }
}
