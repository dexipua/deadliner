package ua.dexipua.deadliner.reminder;

import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.deadline.Deadline;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Bot texts about deadlines. All times are shown in Kyiv time. */
@Component
public class MessageFormatter {

    public static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private static final String[] WEEKDAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд"};
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM, HH:mm");
    // "CS201 (2026-2027)" -> "CS201"
    private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\s*\\(\\d{4}-\\d{4}\\)$");

    public String reminder(List<Deadline> deadlines) {
        return "⏰ Нагадування про дедлайни:\n\n" + lines(deadlines);
    }

    String lines(List<Deadline> deadlines) {
        StringBuilder text = new StringBuilder();
        deadlines.stream()
                .sorted(Comparator.comparing(Deadline::getDueAt))
                .forEach(d -> text.append("• ").append(line(d)).append('\n'));
        return text.toString().strip();
    }

    public String line(Deadline d) {
        return when(d) + " — " + label(d);
    }

    public String label(Deadline d) {
        if (d.getCourseName() == null) {
            return d.getTitle();
        }
        return ACADEMIC_YEAR.matcher(d.getCourseName()).replaceAll("") + ": " + d.getTitle();
    }

    private static String when(Deadline d) {
        ZonedDateTime kyiv = d.getDueAt().atZone(KYIV);
        return WEEKDAYS[kyiv.getDayOfWeek().getValue() - 1] + " " + DATE_TIME.format(kyiv);
    }
}
