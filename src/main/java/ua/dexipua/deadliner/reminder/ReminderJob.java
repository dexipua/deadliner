package ua.dexipua.deadliner.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.bot.InlineButton;
import ua.dexipua.deadliner.bot.Notifier;
import ua.dexipua.deadliner.bot.UserBlockedException;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.reminder.ReminderPlanner.SentKey;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Every minute: finds due reminders and sends each user one merged message. */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private static final int MAX_BUTTON_TITLE = 30;

    private final DeadlineRepository deadlines;
    private final AppUserRepository users;
    private final ReminderSettings settings;
    private final SentReminders sentReminders;
    private final MessageFormatter formatter;
    private final Notifier notifier;
    private final Clock clock;
    private final ReminderPlanner planner = new ReminderPlanner();

    public ReminderJob(DeadlineRepository deadlines, AppUserRepository users, ReminderSettings settings,
                       SentReminders sentReminders, MessageFormatter formatter, Notifier notifier, Clock clock) {
        this.deadlines = deadlines;
        this.users = users;
        this.settings = settings;
        this.sentReminders = sentReminders;
        this.formatter = formatter;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void run() {
        runOnce(clock.instant());
    }

    public void runOnce(Instant now) {
        Map<Long, List<Deadline>> byUser = deadlines.findUpcomingOpenOfActiveUsers(now).stream()
                .collect(Collectors.groupingBy(Deadline::getUserId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<Deadline>> entry : byUser.entrySet()) {
            // one user's failure (e.g. deleted via /stop mid-run) must not skip everyone after them
            try {
                AppUser user = users.findById(entry.getKey()).orElse(null);
                if (user == null) {
                    continue;
                }
                List<Deadline> open = entry.getValue().stream()
                        .filter(d -> GroupFilter.visible(d, user.getSeminarGroup()))
                        .toList();
                var sent = sentReminders.findFor(open.stream().map(Deadline::getId).toList());
                List<DueReminder> due = planner.plan(open, settings.offsets(entry.getKey()), sent, now);
                if (!due.isEmpty()) {
                    send(entry.getKey(), due, now);
                }
            } catch (RuntimeException e) {
                log.error("Reminders failed for user {}", entry.getKey(), e);
            }
        }
    }

    private void send(long userId, List<DueReminder> due, Instant now) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        List<Deadline> distinct = due.stream().map(DueReminder::deadline).distinct().toList();
        List<InlineButton> buttons = distinct.stream()
                .map(d -> new InlineButton("✅ Здано: " + shorten(d.getTitle()), "done:" + d.getId()))
                .toList();
        try {
            notifier.send(user.getTelegramId(), formatter.reminder(distinct), buttons);
        } catch (UserBlockedException e) {
            user.setActive(false);
            users.save(user);
            return;
        } catch (RuntimeException e) {
            // not recorded as sent, so the next run retries while the reminder is still in the grace window
            log.warn("Could not send reminder to user {}: {}", userId, e.getMessage());
            return;
        }
        for (DueReminder r : due) {
            sentReminders.record(new SentKey(r.deadline().getId(), r.offsetMinutes(), r.deadline().getDueAt()), now);
        }
    }

    private static String shorten(String title) {
        return title.length() <= MAX_BUTTON_TITLE ? title : title.substring(0, MAX_BUTTON_TITLE - 1) + "…";
    }
}
