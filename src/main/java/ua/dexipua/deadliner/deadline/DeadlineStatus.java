package ua.dexipua.deadliner.deadline;

public enum DeadlineStatus {
    OPEN,
    DONE,
    /** Disappeared from the Moodle feed before it was due (e.g. the teacher deleted it). */
    GONE
}
