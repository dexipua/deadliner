package ua.dexipua.deadliner.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.bot.Notifier;
import ua.dexipua.deadliner.bot.UserBlockedException;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Sunday 19:00 Kyiv time: the week ahead, for users who have the digest on. */
@Component
public class DigestJob {

    static final String CRON = "0 0 19 * * SUN";

    private static final Logger log = LoggerFactory.getLogger(DigestJob.class);
    private static final Duration WEEK = Duration.ofDays(7);

    private final AppUserRepository users;
    private final DeadlineRepository deadlines;
    private final MessageFormatter formatter;
    private final Notifier notifier;
    private final Clock clock;

    public DigestJob(AppUserRepository users, DeadlineRepository deadlines, MessageFormatter formatter,
                     Notifier notifier, Clock clock) {
        this.users = users;
        this.deadlines = deadlines;
        this.formatter = formatter;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(cron = CRON, zone = "Europe/Kyiv")
    public void run() {
        runOnce(clock.instant());
    }

    public void runOnce(Instant now) {
        for (AppUser user : users.findByActiveTrueAndDigestEnabledTrue()) {
            formatter.digest(deadlines.findOpenDueBetween(user.getId(), now, now.plus(WEEK)).stream()
                            .filter(d -> GroupFilter.visible(d, user.getSeminarGroup()))
                            .toList(), now)
                    .ifPresent(text -> send(user, text));
        }
    }

    private void send(AppUser user, String text) {
        try {
            notifier.send(user.getTelegramId(), text, List.of());
        } catch (UserBlockedException e) {
            user.setActive(false);
            users.save(user);
        } catch (RuntimeException e) {
            log.warn("Could not send digest to user {}: {}", user.getId(), e.getMessage());
        }
    }
}
