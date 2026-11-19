package ua.dexipua.deadliner.reminder;

import ua.dexipua.deadliner.deadline.Deadline;

public record DueReminder(Deadline deadline, int offsetMinutes) {
}
