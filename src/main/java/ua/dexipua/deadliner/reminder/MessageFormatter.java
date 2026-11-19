package ua.dexipua.deadliner.reminder;

import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.Stats;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    /** Sunday digest; empty when nothing is due, so the user is not messaged for nothing. */
    public Optional<String> digest(List<Deadline> nextWeek) {
        if (nextWeek.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("🗓 Дедлайни на наступний тиждень:\n\n" + lines(nextWeek));
    }

    /** Answer to /deadlines. */
    public String list(List<Deadline> deadlines) {
        if (deadlines.isEmpty()) {
            return "🎉 На найближчі 7 днів дедлайнів немає.";
        }
        return "📋 Дедлайни на 7 днів:\n\n" + lines(deadlines);
    }

    /** Answer to /stats. */
    public String stats(Stats stats) {
        if (stats.byCourse().isEmpty()) {
            return "📊 Поки що немає даних для статистики.";
        }
        StringBuilder text = new StringBuilder("📊 Твоя статистика\n\n").append(counts(stats.total()));
        Integer percent = stats.total().onTimePercent();
        if (percent != null) {
            text.append("\nВчасно здано: ").append(percent).append('%');
        }
        text.append("\n\nПо курсах:");
        stats.byCourse().forEach((course, c) ->
                text.append("\n• ").append(ACADEMIC_YEAR.matcher(course).replaceAll("")).append(" — ").append(counts(c)));
        text.append("\n\n«Здано» рахується, коли ти натискаєш ✅ під нагадуванням.");
        return text.toString();
    }

    private static String counts(Stats.Counts c) {
        return "✅ " + c.onTime() + " · ⏰ " + c.late() + " · ❌ " + c.overdue() + " · 🔜 " + c.upcoming();
    }

    private String lines(List<Deadline> deadlines) {
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
