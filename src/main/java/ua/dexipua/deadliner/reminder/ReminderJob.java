package ua.dexipua.deadliner.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.bot.Notifier;
import ua.dexipua.deadliner.bot.UserBlockedException;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.CourseGroups;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.reminder.ReminderPlanner.SentKey;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Every minute: finds due reminders and sends each user one merged message. */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final DeadlineRepository deadlines;
    private final AppUserRepository users;
    private final ReminderSettings settings;
    private final SentReminders sentReminders;
    private final MessageFormatter formatter;
    private final Notifier notifier;
    private final Clock clock;
    private final Snoozes snoozes;
    private final CourseGroups courseGroups;
    private final ReminderPlanner planner = new ReminderPlanner();

    public ReminderJob(DeadlineRepository deadlines, AppUserRepository users, ReminderSettings settings,
                       SentReminders sentReminders, Snoozes snoozes, CourseGroups courseGroups, MessageFormatter formatter, Notifier notifier,
                       Clock clock) {
        this.deadlines = deadlines;
        this.users = users;
        this.settings = settings;
        this.sentReminders = sentReminders;
        this.snoozes = snoozes;
        this.courseGroups = courseGroups;
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
                List<Deadline> open = GroupFilter.visible(entry.getValue(), courseGroups.of(user.getId()));
                var sent = sentReminders.findFor(open.stream().map(Deadline::getId).toList());
                List<DueReminder> due = planner.plan(open, settings.offsets(entry.getKey()), sent, now, user.isQuietHours(),
                        user.getQuietWindow());
                Set<Long> snoozed = snoozes.dueAmong(open.stream().map(Deadline::getId).toList(), now);
                if (!due.isEmpty() || !snoozed.isEmpty()) {
                    send(user, open, due, snoozed, now);
                }
            } catch (RuntimeException e) {
                log.error("Reminders failed for user {}", entry.getKey(), e);
            }
        }
    }

    private void send(AppUser user, List<Deadline> open, List<DueReminder> due, Set<Long> snoozed, Instant now) {
        long userId = user.getId();
        Set<Long> ids = due.stream().map(r -> r.deadline().getId()).collect(Collectors.toCollection(HashSet::new));
        ids.addAll(snoozed);
        List<Deadline> distinct = open.stream().filter(d -> ids.contains(d.getId())).toList();
        try {
            notifier.send(user.getTelegramId(), formatter.reminder(distinct, now), formatter.reminderButtons(distinct));
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
        snoozes.delete(snoozed);
    }
}
