package ua.dexipua.deadliner.reminder;

import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides which reminders are due. A reminder's send time is {@code dueAt - offset}; it fires
 * when that time is within the last {@link #GRACE} and it was not sent for this due date yet.
 * The grace window survives restarts and failed sends; older reminders are skipped on purpose,
 * so a user who connects right before a deadline does not get a burst of stale "3 days left".
 */
public class ReminderPlanner {

    static final Duration GRACE = Duration.ofMinutes(30);

    public record SentKey(long deadlineId, int offsetMinutes, Instant dueAt) {
    }

    public List<DueReminder> plan(List<Deadline> open, Set<Integer> offsets, Set<SentKey> alreadySent, Instant now) {
        return plan(open, offsets, alreadySent, now, false);
    }

    /**
     * With {@code quietHours}, a reminder that falls inside them is moved to their end, or, when the deadline
     * comes before that, to their start.
     */
    public List<DueReminder> plan(List<Deadline> open, Set<Integer> offsets, Set<SentKey> alreadySent, Instant now,
                                  boolean quietHours) {
        return plan(open, offsets, alreadySent, now, quietHours, QuietHours.DEFAULT);
    }

    /** {@code window} is the student's quiet hours; it also matters when they are switched off (see below). */
    public List<DueReminder> plan(List<Deadline> open, Set<Integer> offsets, Set<SentKey> alreadySent, Instant now,
                                  boolean quietHours, QuietHours window) {
        Instant windowStart = now.minus(GRACE);
        List<DueReminder> due = new ArrayList<>();
        for (Deadline d : open) {
            if (d.getStatus() != DeadlineStatus.OPEN || !d.getDueAt().isAfter(now)) {
                continue;
            }
            for (int offset : offsets) {
                Instant onTime = d.getDueAt().minus(Duration.ofMinutes(offset));
                // inside quiet hours: wait for their end, or, if that is too late for the deadline,
                // send it just as they begin instead of in the middle of the night
                Instant morning = !window.isQuiet(onTime) ? onTime
                        : window.end(onTime).isBefore(d.getDueAt()) ? window.end(onTime) : window.start(onTime);
                // with quiet hours off, a reminder held overnight while they were on still goes out at 08:00
                boolean inWindow = inWindow(morning, windowStart, now) || (!quietHours && inWindow(onTime, windowStart, now));
                if (inWindow && !alreadySent.contains(new SentKey(d.getId(), offset, d.getDueAt()))) {
                    due.add(new DueReminder(d, offset));
                }
            }
        }
        return due;
    }

    private static boolean inWindow(Instant at, Instant windowStart, Instant now) {
        return at.isAfter(windowStart) && !at.isAfter(now);
    }
}
