package ua.dexipua.deadliner.reminder;

import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.bot.InlineButton;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.Stats;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ua.dexipua.deadliner.bot.Html.escape;

/** Bot texts about deadlines, in Telegram HTML. All times are shown in Kyiv time. */
@Component
public class MessageFormatter {

    public static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private static final String[] WEEKDAYS = {"пн", "вт", "ср", "чт", "пт", "сб", "нд"};
    private static final String[] MONTHS = {"січня", "лютого", "березня", "квітня", "травня", "червня",
            "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    // "CS201 (2026-2027)" -> "CS201"
    private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\s*\\(\\d{4}-\\d{4}\\)$");

    public String reminder(List<Deadline> deadlines, Instant now) {
        List<Deadline> sorted = sorted(deadlines);
        if (sorted.size() == 1) {
            Deadline d = sorted.getFirst();
            return "⏰ <b>Дедлайн " + relative(d.getDueAt(), now) + "</b>\n\n" + block(d, now);
        }
        StringBuilder text = new StringBuilder("⏰ <b>Наближаються дедлайни</b>");
        for (int i = 0; i < sorted.size(); i++) {
            text.append("\n\n").append(i + 1).append(". ").append(block(sorted.get(i), now));
        }
        return text.toString();
    }

    /** One row per deadline, in the same order as {@link #reminder}. */
    public List<List<InlineButton>> reminderButtons(List<Deadline> deadlines) {
        List<Deadline> sorted = sorted(deadlines);
        List<List<InlineButton>> rows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            String n = sorted.size() == 1 ? "" : (i + 1) + ". ";
            long id = sorted.get(i).getId();
            rows.add(List.of(
                    new InlineButton("✅ " + n + "Здано", "done:" + id),
                    new InlineButton("⏰ " + n + "+1 год", "snooze:" + id)));
        }
        return rows;
    }

    /** Sunday digest; empty when nothing is due, so the user is not messaged for nothing. */
    public Optional<String> digest(List<Deadline> nextWeek, Instant now) {
        if (nextWeek.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("🗓 <b>План на тиждень</b>" + byDay(nextWeek, now));
    }

    /** Answer to /deadlines. */
    public String list(List<Deadline> deadlines, Instant now) {
        if (deadlines.isEmpty()) {
            return "🎉 На найближчі 7 днів дедлайнів немає. Відпочивай!";
        }
        return "📋 <b>Дедлайни на 7 днів</b>" + byDay(deadlines, now);
    }

    /** One line for the "connected" message. */
    public String nearest(Deadline d, Instant now) {
        return "<b>" + escape(label(d)) + "</b>\n🕚 " + date(d.getDueAt()) + " · " + time(d.getDueAt())
                + " (" + relative(d.getDueAt(), now) + ")";
    }

    /** Answer to /stats. */
    public String stats(Stats stats) {
        if (stats.byCourse().isEmpty()) {
            return "📊 Поки що немає даних для статистики.";
        }
        StringBuilder text = new StringBuilder("📊 <b>Твоя статистика</b>\n\n").append(counts(stats.total()));
        Integer percent = stats.total().onTimePercent();
        if (percent != null) {
            text.append("\nВчасно здано: <b>").append(percent).append("%</b>");
        }
        text.append("\n\n<b>По курсах</b>");
        stats.byCourse().forEach((course, c) ->
                text.append("\n• ").append(escape(shortCourse(course))).append(" — ").append(counts(c)));
        text.append("\n\n<i>✅ вчасно · ⏰ із запізненням · ❌ прострочено · 🔜 попереду.\n")
                .append("«Здано» — коли ти натискаєш ✅ під нагадуванням.</i>");
        return text.toString();
    }

    /** Plain text (no HTML): "CS201: Assignment 3". */
    public String label(Deadline d) {
        return d.getCourseName() == null ? d.getTitle() : shortCourse(d.getCourseName()) + ": " + d.getTitle();
    }

    /** "15:00" in Kyiv time. */
    public String time(Instant at) {
        return TIME.format(at.atZone(KYIV));
    }

    /** "через 3 години", "через 1 день". Hours are rounded, so a reminder sent a few seconds late reads the same. */
    public String relative(Instant due, Instant now) {
        long minutes = Duration.between(now, due).toMinutes();
        if (minutes < 0) {
            return "вже минув";
        }
        if (minutes < 60) {
            return "через " + Math.max(minutes, 1) + " хв";
        }
        long hours = Math.round(minutes / 60.0);
        if (hours < 24) {
            return "через " + hours + " " + plural(hours, "годину", "години", "годин");
        }
        long days = hours / 24;
        return "через " + days + " " + plural(days, "день", "дні", "днів");
    }

    private String block(Deadline d, Instant now) {
        StringBuilder text = new StringBuilder();
        if (d.getCourseName() != null) {
            text.append("📘 <b>").append(escape(shortCourse(d.getCourseName()))).append("</b>\n");
        }
        return text.append(escape(d.getTitle())).append("\n🕚 ").append(date(d.getDueAt())).append(" · ")
                .append(time(d.getDueAt())).append(" (").append(relative(d.getDueAt(), now)).append(')')
                .toString();
    }

    private String byDay(List<Deadline> deadlines, Instant now) {
        LocalDate today = now.atZone(KYIV).toLocalDate();
        Map<LocalDate, List<Deadline>> days = sorted(deadlines).stream()
                .collect(Collectors.groupingBy(d -> d.getDueAt().atZone(KYIV).toLocalDate(),
                        LinkedHashMap::new, Collectors.toList()));
        StringBuilder text = new StringBuilder();
        days.forEach((day, list) -> {
            text.append("\n\n<b>").append(dayName(day, today)).append("</b>");
            for (Deadline d : list) {
                text.append("\n• <b>").append(time(d.getDueAt())).append("</b> · ");
                if (d.getCourseName() != null) {
                    text.append(escape(shortCourse(d.getCourseName()))).append(" — ");
                }
                text.append(escape(d.getTitle()));
            }
        });
        return text.toString();
    }

    private static String dayName(LocalDate day, LocalDate today) {
        if (day.equals(today)) {
            return "Сьогодні";
        }
        if (day.equals(today.plusDays(1))) {
            return "Завтра";
        }
        String weekday = WEEKDAYS[day.getDayOfWeek().getValue() - 1];
        return Character.toUpperCase(weekday.charAt(0)) + weekday.substring(1) + ", "
                + day.getDayOfMonth() + " " + MONTHS[day.getMonthValue() - 1];
    }

    private static String date(Instant at) {
        ZonedDateTime kyiv = at.atZone(KYIV);
        return WEEKDAYS[kyiv.getDayOfWeek().getValue() - 1] + ", " + kyiv.getDayOfMonth() + " "
                + MONTHS[kyiv.getMonthValue() - 1];
    }

    private static String counts(Stats.Counts c) {
        return "✅ " + c.onTime() + " · ⏰ " + c.late() + " · ❌ " + c.overdue() + " · 🔜 " + c.upcoming();
    }

    private static String shortCourse(String course) {
        return ACADEMIC_YEAR.matcher(course).replaceAll("");
    }

    private static List<Deadline> sorted(List<Deadline> deadlines) {
        return deadlines.stream().sorted(Comparator.comparing(Deadline::getDueAt)).toList();
    }

    private static String plural(long n, String one, String few, String many) {
        long mod100 = n % 100;
        long mod10 = n % 10;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return few;
        }
        return many;
    }
}
