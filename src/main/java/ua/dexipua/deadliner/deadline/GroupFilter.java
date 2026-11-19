package ua.dexipua.deadliner.deadline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KSE's Moodle exports quizzes for every seminar group ("Квіз 8 · Група 5", "… · Група 7"),
 * and the group number is per course. Once a student sets their group in a course,
 * that course's quizzes for other groups are hidden.
 */
public final class GroupFilter {

    private static final Pattern GROUP = Pattern.compile("(?:Група|Group)\\s*(\\d{1,3})(?!\\d)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private GroupFilter() {
    }

    public static boolean visible(Deadline d, Map<String, Integer> groupByCourse) {
        Integer group = d.getCourseName() == null ? null : groupByCourse.get(d.getCourseName());
        if (group == null) {
            return true;
        }
        Matcher m = GROUP.matcher(d.getTitle());
        return !m.find() || Integer.parseInt(m.group(1)) == group;
    }

    public static List<Deadline> visible(List<Deadline> deadlines, Map<String, Integer> groupByCourse) {
        return deadlines.stream().filter(d -> visible(d, groupByCourse)).toList();
    }

    public static boolean mentionsGroup(Deadline d) {
        return GROUP.matcher(d.getTitle()).find();
    }

    /** Courses that have group-specific deadlines, sorted by name. */
    public static List<String> coursesWithGroups(List<Deadline> deadlines) {
        return deadlines.stream()
                .filter(GroupFilter::mentionsGroup)
                .map(Deadline::getCourseName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
