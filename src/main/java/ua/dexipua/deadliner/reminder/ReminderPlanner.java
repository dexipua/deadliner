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
        Instant windowStart = now.minus(GRACE);
        List<DueReminder> due = new ArrayList<>();
        for (Deadline d : open) {
            if (d.getStatus() != DeadlineStatus.OPEN || !d.getDueAt().isAfter(now)) {
                continue;
            }
            for (int offset : offsets) {
                Instant sendAt = d.getDueAt().minus(Duration.ofMinutes(offset));
                boolean inWindow = sendAt.isAfter(windowStart) && !sendAt.isAfter(now);
                if (inWindow && !alreadySent.contains(new SentKey(d.getId(), offset, d.getDueAt()))) {
                    due.add(new DueReminder(d, offset));
                }
            }
        }
        return due;
    }
}
