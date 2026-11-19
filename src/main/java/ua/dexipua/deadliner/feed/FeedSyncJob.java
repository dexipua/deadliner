package ua.dexipua.deadliner.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.bot.Notifier;
import ua.dexipua.deadliner.bot.UserBlockedException;
import ua.dexipua.deadliner.feed.FeedSyncService.SyncOutcome;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Re-reads every active user's Moodle feed, one at a time to be polite to Moodle. */
@Component
public class FeedSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FeedSyncJob.class);

    /** Warn the user once, on exactly this many failures in a row. */
    static final int WARN_AFTER_FAILURES = 3;
    static final String BROKEN_LINK_TEXT =
            "⚠️ Посилання на календар Moodle перестало працювати. Надішли мені нове посилання — інструкція в /start.";

    private final FeedSyncService service;
    private final CalendarFeedRepository feeds;
    private final AppUserRepository users;
    private final Notifier notifier;
    private final Clock clock;

    public FeedSyncJob(FeedSyncService service, CalendarFeedRepository feeds, AppUserRepository users,
                       Notifier notifier, Clock clock) {
        this.service = service;
        this.feeds = feeds;
        this.users = users;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT1M")
    public void run() {
        runOnce(clock.instant());
    }

    public void runOnce(Instant now) {
        for (CalendarFeed feed : feeds.findAllOfActiveUsers()) {
            // one broken feed (or a user who just hit /stop) must not stop the sync for everyone else
            try {
                SyncOutcome outcome = service.sync(feed.getId(), now);
                if (outcome instanceof SyncOutcome.Failed(int failures) && failures == WARN_AFTER_FAILURES) {
                    warn(feed.getUserId());
                }
            } catch (RuntimeException e) {
                log.error("Sync failed for feed {}", feed.getId(), e);
            }
        }
    }

    private void warn(long userId) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        try {
            notifier.send(user.getTelegramId(), BROKEN_LINK_TEXT, List.of());
        } catch (UserBlockedException e) {
            user.setActive(false);
            users.save(user);
        } catch (RuntimeException e) {
            log.warn("Could not warn user {} about a broken link: {}", userId, e.getMessage());
        }
    }
}
