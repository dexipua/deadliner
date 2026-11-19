package ua.dexipua.deadliner.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ua.dexipua.deadliner.bot.InlineButton;
import ua.dexipua.deadliner.deadline.Deadline;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFormatterTest {

    // Thursday 19 Nov 2026, 23:59 Kyiv (UTC+2)
    static final Instant NOW = Instant.parse("2026-11-19T21:59:00Z");

    private final MessageFormatter formatter = new MessageFormatter();

    private static Deadline deadline(long id, String course, String title, String dueAt) {
        Deadline d = new Deadline(1L, title);
        ReflectionTestUtils.setField(d, "id", id);
        d.setCourseName(course);
        d.setTitle(title);
        d.setDueAt(Instant.parse(dueAt));
        return d;
    }

    private static Deadline assignment() {
        return deadline(7, "CS201 (2026-2027)", "Assignment 3 - API & Trading", "2026-11-20T21:59:00Z");
    }

    @Test
    void singleReminderHasHeadlineCourseTitleAndRelativeTime() {
        String text = formatter.reminder(List.of(assignment()), NOW);

        assertThat(text)
                .contains("<b>Дедлайн через 1 день</b>")
                .contains("📘 <b>CS201</b>")
                .contains("Assignment 3 - API &amp; Trading")
                .contains("пт, 20 листопада · 23:59");
    }

    @Test
    void reminderJustAfterSendTimeStillSaysOneDay() {
        assertThat(formatter.reminder(List.of(assignment()), NOW.plusSeconds(40))).contains("через 1 день");
    }

    @Test
    void threeHourReminderSaysThreeHours() {
        assertThat(formatter.reminder(List.of(assignment()), Instant.parse("2026-11-20T18:59:00Z")))
                .contains("через 3 години");
    }

    @Test
    void ukrainianPluralsForDays() {
        assertThat(formatter.relative(Instant.parse("2026-11-24T21:59:00Z"), NOW)).isEqualTo("через 5 днів");
        assertThat(formatter.relative(Instant.parse("2026-11-22T21:59:00Z"), NOW)).isEqualTo("через 3 дні");
        assertThat(formatter.relative(NOW.plusSeconds(25 * 60), NOW)).isEqualTo("через 25 хв");
    }

    @Test
    void titlesFromMoodleAreHtmlEscaped() {
        String text = formatter.reminder(List.of(deadline(1, null, "<script>x</script>", "2026-11-20T21:59:00Z")), NOW);

        assertThat(text).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void singleReminderButtonsAreOneCompactRow() {
        List<List<InlineButton>> rows = formatter.reminderButtons(List.of(assignment()));

        assertThat(rows).containsExactly(List.of(
                new InlineButton("✅ Здано", "done:7"), new InlineButton("⏰ +1 год", "snooze:7")));
    }

    @Test
    void severalRemindersAreNumberedWithARowEach() {
        Deadline later = deadline(8, "MATH101 (2026-2027)", "Mind Map 3", "2026-11-21T21:59:00Z");

        String text = formatter.reminder(List.of(later, assignment()), NOW);
        List<List<InlineButton>> rows = formatter.reminderButtons(List.of(later, assignment()));

        assertThat(text).contains("<b>Наближаються дедлайни</b>");
        assertThat(text.indexOf("1. ")).isLessThan(text.indexOf("Assignment 3"));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).extracting(InlineButton::text).containsExactly("✅ 1. Здано", "⏰ 1. +1 год");
        assertThat(rows.getFirst().getFirst().callbackData()).isEqualTo("done:7");
    }

    @Test
    void listGroupsByDayWithTodayAndTomorrow() {
        Deadline today = deadline(1, "ENG121 (2026-2027)", "HW1", "2026-11-19T21:59:30Z");
        Deadline nextWeek = deadline(3, null, "Essay", "2026-11-23T21:59:00Z");

        String text = formatter.list(List.of(), List.of(nextWeek, assignment(), today), Instant.parse("2026-11-19T10:00:00Z"));

        assertThat(text).contains("<b>Сьогодні</b>").contains("<b>Завтра</b>").contains("<b>Пн, 23 листопада</b>");
        assertThat(text).contains("2. <b>23:59</b> · CS201 — Assignment 3 - API &amp; Trading");
        assertThat(text.indexOf("Сьогодні")).isLessThan(text.indexOf("Завтра"));
    }

    @Test
    void overdueComeFirstAndNumberingContinues() {
        Deadline overdue = deadline(9, "MATH101 (2026-2027)", "Mind Map 2", "2026-11-10T21:59:00Z");

        String text = formatter.list(List.of(overdue), List.of(assignment()), NOW);

        assertThat(text).contains("⚠️ <b>Прострочені</b>").contains("1. <b>вт, 10 листопада</b> · MATH101 — Mind Map 2")
                .contains("2. <b>23:59</b> · CS201");
        assertThat(text.indexOf("Прострочені")).isLessThan(text.indexOf("Завтра"));
    }

    @Test
    void listButtonsMarkEachNumberedDeadlineDone() {
        Deadline overdue = deadline(9, "MATH101 (2026-2027)", "Mind Map 2", "2026-11-10T21:59:00Z");

        List<List<InlineButton>> rows = formatter.listButtons(List.of(overdue), List.of(assignment()));

        assertThat(rows).containsExactly(List.of(new InlineButton("✅ 1", "done:9"), new InlineButton("✅ 2", "done:7")));
    }

    @Test
    void listButtonsWrapAfterFivePerRow() {
        List<Deadline> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            many.add(deadline(i, null, "T" + i, "2026-11-2" + i + "T10:00:00Z"));
        }

        assertThat(formatter.listButtons(List.of(), many)).extracting(List::size).containsExactly(5, 2);
    }

    @Test
    void toastLabelIsShortEnoughForTelegram() {
        Deadline d = deadline(1, "CS201 (2026-2027)", "x".repeat(300), "2026-11-20T21:59:00Z");

        String label = formatter.toastLabel(d);

        assertThat(label).hasSizeLessThanOrEqualTo(150).endsWith("…");
    }

    private static List<Deadline> many(int count, String firstDue) {
        List<Deadline> list = new java.util.ArrayList<>();
        Instant due = Instant.parse(firstDue);
        for (int i = 0; i < count; i++) {
            list.add(deadline(100 + i, "MATH101 (2026-2027)", "Квіз " + i + " · Заняття 17 · Група " + (i % 10 + 1), due.plusSeconds(i * 600L).toString()));
        }
        return list;
    }

    @Test
    void longListIsCappedAndFitsInOneMessage() {
        List<Deadline> upcoming = many(50, "2026-11-20T08:00:00Z");

        String text = formatter.list(List.of(), upcoming, NOW);
        int buttons = formatter.listButtons(List.of(), upcoming).stream().mapToInt(List::size).sum();

        assertThat(text.length()).isLessThan(4096);
        assertThat(text).contains("і ще 20").contains("/group");
        assertThat(buttons).isEqualTo(30);
    }

    @Test
    void overdueAreCappedAtTenAndNumberingStillMatchesButtons() {
        List<Deadline> overdue = many(15, "2026-11-10T08:00:00Z");
        List<Deadline> upcoming = List.of(assignment());

        String text = formatter.list(overdue, upcoming, NOW);
        List<List<InlineButton>> rows = formatter.listButtons(overdue, upcoming);

        assertThat(text).contains("10. ").contains("11. <b>23:59</b> · CS201").doesNotContain("12. ");
        assertThat(rows.stream().flatMap(List::stream).toList()).hasSize(11)
                .last().extracting(InlineButton::callbackData).isEqualTo("done:7");
    }

    @Test
    void emptyListSaysNothingIsDue() {
        assertThat(formatter.list(List.of(), List.of(), NOW)).contains("немає");
        assertThat(formatter.listButtons(List.of(), List.of())).isEmpty();
    }

    @Test
    void emptyDigestGivesNoMessage() {
        assertThat(formatter.digest(List.of(), NOW)).isEmpty();
    }

    @Test
    void digestHasTitleAndDays() {
        assertThat(formatter.digest(List.of(assignment()), NOW)).get().asString()
                .contains("План на тиждень").contains("Завтра").contains("CS201");
    }

    @Test
    void statsShowPercentWithProgressBarAndCourseBlocks() {
        Deadline late = deadline(1, "CS201 (2026-2027)", "A1", "2026-11-10T21:59:00Z");
        late.markDone(Instant.parse("2026-11-11T08:00:00Z"));
        Deadline onTime = deadline(2, "CS201 (2026-2027)", "A2", "2026-11-12T21:59:00Z");
        onTime.markDone(Instant.parse("2026-11-12T20:00:00Z"));
        Deadline overdue = deadline(3, "MATH101 (2026-2027)", "Mind Map", "2026-11-15T21:59:00Z");
        Deadline upcoming = deadline(4, "MATH101 (2026-2027)", "Quiz", "2026-11-25T21:59:00Z");

        String text = formatter.stats(ua.dexipua.deadliner.deadline.Stats.of(List.of(late, onTime, overdue, upcoming), NOW));

        assertThat(text)
                .contains("Вчасно здано: <b>33%</b>")
                .contains("▓▓▓░░░░░░░")
                .contains("📘 <b>CS201</b>  ▓▓▓▓▓░░░░░ 50%")
                .contains("📘 <b>MATH101</b>  ░░░░░░░░░░ 0%")
                .contains("🔜 1");
    }

    @Test
    void statsForCourseWithNothingFinishedShowsNoPercent() {
        Deadline upcoming = deadline(4, "MATH101 (2026-2027)", "Quiz", "2026-11-25T21:59:00Z");

        String text = formatter.stats(ua.dexipua.deadliner.deadline.Stats.of(List.of(upcoming), NOW));

        assertThat(text).contains("📘 <b>MATH101</b>\n").doesNotContain("%");
    }

    @Test
    void summerTimeIsShownInKyivTime() {
        Deadline exam = deadline(1, null, "Exam", "2027-03-28T20:59:00Z");

        assertThat(formatter.reminder(List.of(exam), Instant.parse("2027-03-27T20:59:00Z"))).contains("нд, 28 березня · 23:59");
    }

    @Test
    void winterTimeIsShownInKyivTime() {
        Deadline exam = deadline(1, null, "Exam", "2026-12-01T21:59:00Z");

        assertThat(formatter.reminder(List.of(exam), NOW)).contains("вт, 1 грудня · 23:59");
    }
}
