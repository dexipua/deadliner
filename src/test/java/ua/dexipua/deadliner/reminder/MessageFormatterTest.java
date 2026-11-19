package ua.dexipua.deadliner.reminder;

import org.junit.jupiter.api.Test;
import ua.dexipua.deadliner.deadline.Deadline;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFormatterTest {

    private final MessageFormatter formatter = new MessageFormatter();

    private static Deadline deadline(String course, String title, String dueAt) {
        Deadline d = new Deadline(1L, title);
        d.setCourseName(course);
        d.setTitle(title);
        d.setDueAt(Instant.parse(dueAt));
        return d;
    }

    @Test
    void reminderLineUsesKyivTimeAndShortCourseName() {
        String text = formatter.reminder(List.of(
                deadline("CS201 (2026-2027)", "Assignment 3", "2026-11-20T21:59:00Z")));

        assertThat(text).contains("Пт 20.11, 23:59 — CS201: Assignment 3");
    }

    @Test
    void reminderWithoutCourseShowsOnlyTitle() {
        String text = formatter.reminder(List.of(deadline(null, "Зустріч", "2026-11-20T21:59:00Z")));

        assertThat(text).contains("Пт 20.11, 23:59 — Зустріч");
    }

    @Test
    void summerTimeIsUtcPlusThree() {
        String text = formatter.reminder(List.of(deadline(null, "Exam", "2027-03-28T20:59:00Z")));

        assertThat(text).contains("Нд 28.03, 23:59 — Exam");
    }

    @Test
    void winterTimeIsUtcPlusTwo() {
        String text = formatter.reminder(List.of(deadline(null, "Exam", "2026-12-01T21:59:00Z")));

        assertThat(text).contains("Вт 01.12, 23:59 — Exam");
    }

    @Test
    void emptyDigestGivesNoMessage() {
        assertThat(formatter.digest(List.of())).isEmpty();
    }

    @Test
    void digestListsTheWeek() {
        assertThat(formatter.digest(List.of(deadline("CS201 (2026-2027)", "A3", "2026-11-20T21:59:00Z"))))
                .get().asString().contains("тиждень").contains("CS201: A3");
    }

    @Test
    void emptyListSaysNothingIsDue() {
        assertThat(formatter.list(List.of())).contains("немає");
    }

    @Test
    void reminderListsDeadlinesInDueOrder() {
        String text = formatter.reminder(List.of(
                deadline("B", "later", "2026-11-22T10:00:00Z"),
                deadline("A", "sooner", "2026-11-21T10:00:00Z")));

        assertThat(text.indexOf("sooner")).isLessThan(text.indexOf("later"));
    }
}
