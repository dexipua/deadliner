package ua.dexipua.deadliner.deadline;

import org.springframework.stereotype.Service;
import ua.dexipua.deadliner.reminder.QuietHours;
import ua.dexipua.deadliner.reminder.Snoozes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What a student can do with a deadline. Shared by the bot and the web API so the rules stay the same. */
@Service
public class DeadlineActions {

    static final Duration SNOOZE = Duration.ofHours(1);
    static final Duration SNOOZE_BEFORE_DUE = Duration.ofMinutes(10);

    public sealed interface SnoozeResult {
        record Snoozed(Instant remindAt) implements SnoozeResult {
        }

        record TooLate() implements SnoozeResult {
        }

        record NotFound() implements SnoozeResult {
        }
    }

    private final DeadlineRepository deadlines;
    private final Snoozes snoozes;
    private final Clock clock;

    public DeadlineActions(DeadlineRepository deadlines, Snoozes snoozes, Clock clock) {
        this.deadlines = deadlines;
        this.snoozes = snoozes;
        this.clock = clock;
    }

    /** Only the owner may act on a deadline, whatever id the request carries. */
    public Optional<Deadline> own(long userId, long deadlineId) {
        return deadlines.findById(deadlineId).filter(d -> d.getUserId() == userId);
    }

    /** Empty when it is not the user's or it disappeared from Moodle. Pressing it twice is harmless. */
    public Optional<Deadline> markDone(long userId, long deadlineId) {
        Optional<Deadline> deadline = own(userId, deadlineId).filter(d -> d.getStatus() != DeadlineStatus.GONE);
        deadline.filter(d -> d.getStatus() == DeadlineStatus.OPEN).ifPresent(d -> {
            d.markDone(clock.instant());
            deadlines.save(d);
            snoozes.delete(List.of(d.getId()));
        });
        return deadline;
    }

    public Optional<Deadline> undo(long userId, long deadlineId) {
        Optional<Deadline> deadline = own(userId, deadlineId).filter(d -> d.getStatus() != DeadlineStatus.GONE);
        deadline.filter(d -> d.getStatus() == DeadlineStatus.DONE).ifPresent(d -> {
            d.reopen();
            deadlines.save(d);
        });
        return deadline;
    }

    /** "+1 hour", but never later than 10 minutes before the deadline: reminders stop at the deadline. */
    public SnoozeResult snooze(long userId, long deadlineId) {
        return snooze(userId, deadlineId, null);
    }

    /** With {@code quiet} (the student's quiet hours, or null when off), a snooze that lands inside them waits for their end. */
    public SnoozeResult snooze(long userId, long deadlineId, QuietHours quiet) {
        Optional<Deadline> deadline = own(userId, deadlineId).filter(d -> d.getStatus() == DeadlineStatus.OPEN);
        if (deadline.isEmpty()) {
            return new SnoozeResult.NotFound();
        }
        Instant now = clock.instant();
        Instant remindAt = now.plus(SNOOZE);
        Instant latest = deadline.get().getDueAt().minus(SNOOZE_BEFORE_DUE);
        if (remindAt.isAfter(latest)) {
            if (!latest.isAfter(now)) {
                return new SnoozeResult.TooLate();
            }
            remindAt = latest;
        }
        if (quiet != null && quiet.isQuiet(remindAt) && quiet.end(remindAt).isBefore(latest)) {
            remindAt = quiet.end(remindAt);
        }
        snoozes.snooze(deadline.get().getId(), remindAt);
        return new SnoozeResult.Snoozed(remindAt);
    }
}
