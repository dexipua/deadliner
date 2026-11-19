package ua.dexipua.deadliner.deadline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KSE's Moodle exports quizzes for every seminar group ("Квіз 8 · Група 5", "… · Група 7").
 * Once a student sets their group, deadlines of other groups are hidden.
 */
public final class GroupFilter {

    private static final Pattern GROUP = Pattern.compile("(?:Група|Group)\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private GroupFilter() {
    }

    public static boolean visible(Deadline d, Integer userGroup) {
        if (userGroup == null) {
            return true;
        }
        Matcher m = GROUP.matcher(d.getTitle());
        return !m.find() || Integer.parseInt(m.group(1)) == userGroup;
    }

    public static boolean mentionsGroup(Deadline d) {
        return GROUP.matcher(d.getTitle()).find();
    }
}
