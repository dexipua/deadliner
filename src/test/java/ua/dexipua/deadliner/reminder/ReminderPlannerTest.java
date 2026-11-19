package ua.dexipua.deadliner.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ua.dexipua.deadliner.deadline.Deadline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderPlannerTest {

    static final Instant NOW = Instant.parse("2026-11-19T21:59:30Z");
    static final Set<Integer> DEFAULT = Set.of(4320, 1440, 180);

    private final ReminderPlanner planner = new ReminderPlanner();

    private static Deadline deadline(long id, Instant dueAt) {
        Deadline d = new Deadline(1L, "uid-" + id);
        ReflectionTestUtils.setField(d, "id", id);
        d.setTitle("Assignment " + id);
        d.setDueAt(dueAt);
        return d;
    }

    private List<DueReminder> plan(List<Deadline> open, Set<ReminderPlanner.SentKey> sent) {
        return planner.plan(open, DEFAULT, sent, NOW);
    }

    @Test
    void oneDayReminderFiresWhenItsTimeComes() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T21:59:00Z"));

        assertThat(plan(List.of(d), Set.of())).containsExactly(new DueReminder(d, 1440));
    }

    @Test
    void reminderAlreadySentDoesNotFireAgain() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T21:59:00Z"));
        var sent = Set.of(new ReminderPlanner.SentKey(1, 1440, d.getDueAt()));

        assertThat(plan(List.of(d), sent)).isEmpty();
    }

    @Test
    void freshConnectionCloseToDeadlineGetsNoBurstOfOldReminders() {
        Deadline d = deadline(1, NOW.plus(Duration.ofHours(2)));

        assertThat(plan(List.of(d), Set.of())).isEmpty();
    }

    @Test
    void movedDeadlineGetsItsReminderAgain() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T21:59:00Z"));
        var sentForOldDate = Set.of(new ReminderPlanner.SentKey(1, 1440, Instant.parse("2026-11-18T21:59:00Z")));

        assertThat(plan(List.of(d), sentForOldDate)).containsExactly(new DueReminder(d, 1440));
    }

    @Test
    void doneDeadlineNeverFires() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T21:59:00Z"));
        d.markDone(NOW);

        assertThat(plan(List.of(d), Set.of())).isEmpty();
    }

    // Kyiv is UTC+2 in November: 00:00Z = 02:00 Kyiv, 06:00Z = 08:00 Kyiv
    static final Instant DUE_AT_2AM_KYIV = Instant.parse("2026-11-21T00:00:00Z");

    @Test
    void nightReminderIsHeldDuringQuietHours() {
        Deadline d = deadline(1, DUE_AT_2AM_KYIV);

        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-20T00:00:30Z"), true)).isEmpty();
    }

    @Test
    void heldReminderIsSentAtEightInTheMorning() {
        Deadline d = deadline(1, DUE_AT_2AM_KYIV);

        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-20T06:00:30Z"), true))
                .containsExactly(new DueReminder(d, 1440));
    }

    @Test
    void urgentReminderIsSentAtNightAnyway() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T01:00:00Z")); // 03:00 Kyiv, before quiet hours end

        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-19T22:00:30Z"), true))
                .containsExactly(new DueReminder(d, 180));
    }

    @Test
    void reminderHeldOvernightIsStillSentIfQuietHoursWereTurnedOff() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T08:00:00Z")); // 10:00 Kyiv; 3 h reminder at 07:00, held to 08:00

        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-20T06:00:30Z"), false))
                .containsExactly(new DueReminder(d, 180));
    }

    @Test
    void customQuietHoursHoldTheReminderUntilTheirEnd() {
        Deadline d = deadline(1, Instant.parse("2026-11-20T21:00:00Z")); // 23:00 Kyiv; 1-day reminder at 23:00 Kyiv on 19th
        QuietHours earlyBird = new QuietHours(21, 6);

        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-19T21:00:30Z"), true, earlyBird)).isEmpty();
        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-20T04:00:30Z"), true, earlyBird))
                .containsExactly(new DueReminder(d, 1440)); // 06:00 Kyiv
    }

    @Test
    void withoutQuietHoursNightReminderGoesOutOnTime() {
        Deadline d = deadline(1, DUE_AT_2AM_KYIV);

        assertThat(planner.plan(List.of(d), DEFAULT, Set.of(), Instant.parse("2026-11-20T00:00:30Z"), false))
                .containsExactly(new DueReminder(d, 1440));
    }

    @Test
    void reminderMissedByMoreThanGraceIsSkipped() {
        Deadline d = deadline(1, NOW.plus(Duration.ofMinutes(1440)).minus(ReminderPlanner.GRACE).minusSeconds(1));

        assertThat(plan(List.of(d), Set.of())).isEmpty();
    }
}
