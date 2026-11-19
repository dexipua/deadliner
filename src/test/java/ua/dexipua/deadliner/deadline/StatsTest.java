package ua.dexipua.deadliner.deadline;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsTest {

    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private static Deadline d(String course, String due, DeadlineStatus status, String doneAt) {
        Deadline d = new Deadline(1L, course + due);
        d.setCourseName(course);
        d.setTitle("t");
        d.setDueAt(Instant.parse(due));
        if (status == DeadlineStatus.DONE) {
            d.markDone(Instant.parse(doneAt));
        } else {
            d.setStatus(status);
        }
        return d;
    }

    private final List<Deadline> sample = List.of(
            d("CS201 (2026-2027)", "2026-11-10T21:59:00Z", DeadlineStatus.DONE, "2026-11-10T20:00:00Z"),
            d("CS201 (2026-2027)", "2026-11-12T21:59:00Z", DeadlineStatus.DONE, "2026-11-13T08:00:00Z"),
            d("CS201 (2026-2027)", "2026-11-15T21:59:00Z", DeadlineStatus.OPEN, null),
            d("MATH101 (2026-2027)", "2026-11-25T21:59:00Z", DeadlineStatus.OPEN, null),
            d("MATH101 (2026-2027)", "2026-11-26T21:59:00Z", DeadlineStatus.GONE, null));

    @Test
    void countsTotals() {
        Stats.Counts total = Stats.of(sample, NOW).total();

        assertThat(total).isEqualTo(new Stats.Counts(1, 1, 1, 1));
    }

    @Test
    void groupsByCourseAndIgnoresGone() {
        Stats stats = Stats.of(sample, NOW);

        assertThat(stats.byCourse()).containsEntry("CS201 (2026-2027)", new Stats.Counts(1, 1, 1, 0))
                .containsEntry("MATH101 (2026-2027)", new Stats.Counts(0, 0, 0, 1));
    }

    @Test
    void onTimeShareIgnoresUpcoming() {
        assertThat(Stats.of(sample, NOW).total().onTimePercent()).isEqualTo(33);
    }

    @Test
    void emptyHasNoPercent() {
        assertThat(Stats.of(List.of(), NOW).total().onTimePercent()).isNull();
    }
}
