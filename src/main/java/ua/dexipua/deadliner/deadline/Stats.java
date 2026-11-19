package ua.dexipua.deadliner.deadline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How a student is doing. "Done" means tapped ✅ in Deadliner; Moodle does not tell us about
 * submissions, so an unmarked past deadline counts as overdue. GONE deadlines are ignored.
 */
public record Stats(Counts total, Map<String, Counts> byCourse) {

    public record Counts(int onTime, int late, int overdue, int upcoming) {

        Counts plus(Counts o) {
            return new Counts(onTime + o.onTime, late + o.late, overdue + o.overdue, upcoming + o.upcoming);
        }

        /** Share of finished deadlines handed in on time; null when nothing is finished yet. */
        public Integer onTimePercent() {
            int finished = onTime + late + overdue;
            return finished == 0 ? null : onTime * 100 / finished;
        }
    }

    private static final String NO_COURSE = "Інше";

    public static Stats of(List<Deadline> deadlines, Instant now) {
        Counts total = new Counts(0, 0, 0, 0);
        Map<String, Counts> byCourse = new TreeMap<>();
        for (Deadline d : deadlines) {
            Counts one = classify(d, now);
            if (one == null) {
                continue;
            }
            total = total.plus(one);
            String course = d.getCourseName() == null ? NO_COURSE : d.getCourseName();
            byCourse.merge(course, one, Counts::plus);
        }
        return new Stats(total, byCourse);
    }

    private static Counts classify(Deadline d, Instant now) {
        return switch (d.getStatus()) {
            case GONE -> null;
            case DONE -> d.getDoneAt().isAfter(d.getDueAt()) ? new Counts(0, 1, 0, 0) : new Counts(1, 0, 0, 0);
            case OPEN -> d.getDueAt().isAfter(now) ? new Counts(0, 0, 0, 1) : new Counts(0, 0, 1, 0);
        };
    }
}
