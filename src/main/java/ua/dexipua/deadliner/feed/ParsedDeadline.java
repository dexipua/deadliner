package ua.dexipua.deadliner.feed;

import java.time.Instant;

/** One deadline as read from the feed. {@code courseName} and {@code url} may be null. */
public record ParsedDeadline(String uid, String courseName, String title, Instant dueAt, String url) {
}
