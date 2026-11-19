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
        return Optional.of("🗓 <b>План на тиждень</b>" + byDay(nextWeek, now, null));
    }

    /** Answer to /deadlines: overdue first, then the week by day, numbered to match {@link #listButtons}. */
    public String list(List<Deadline> overdue, List<Deadline> upcoming, Instant now) {
        if (overdue.isEmpty() && upcoming.isEmpty()) {
            return "🎉 На найближчі 7 днів дедлайнів немає. Відпочивай!";
        }
        StringBuilder text = new StringBuilder("📋 <b>Дедлайни на 7 днів</b>");
        List<Deadline> late = sorted(overdue);
        if (!late.isEmpty()) {
            text.append("\n\n⚠️ <b>Прострочені</b>");
            for (int i = 0; i < late.size(); i++) {
                Deadline d = late.get(i);
                text.append('\n').append(i + 1).append(". <b>").append(date(d.getDueAt())).append("</b> · ").append(item(d));
            }
        }
        text.append(byDay(upcoming, now, late.size()));
        if (!late.isEmpty() || !upcoming.isEmpty()) {
            text.append("\n\n<i>Здав? Натисни номер нижче.</i>");
        }
        return text.toString();
    }

    /** "✅ n" for every deadline in {@link #list}, five per row. */
    public List<List<InlineButton>> listButtons(List<Deadline> overdue, List<Deadline> upcoming) {
        List<Deadline> all = new ArrayList<>(sorted(overdue));
        all.addAll(sorted(upcoming));
        List<List<InlineButton>> rows = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 5) {
            List<InlineButton> row = new ArrayList<>();
            for (int j = i; j < Math.min(i + 5, all.size()); j++) {
                row.add(new InlineButton("✅ " + (j + 1), "done:" + all.get(j).getId()));
            }
            rows.add(row);
        }
        return rows;
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
        Stats.Counts total = stats.total();
        StringBuilder text = new StringBuilder("📊 <b>Твоя статистика</b>\n");
        Integer percent = total.onTimePercent();
        if (percent != null) {
            text.append("\nВчасно здано: <b>").append(percent).append("%</b>\n").append(bar(percent));
        }
        text.append("\n✅ ").append(total.onTime()).append(" вчасно · ⏰ ").append(total.late()).append(" із запізненням")
                .append("\n❌ ").append(total.overdue()).append(" прострочено · 🔜 ").append(total.upcoming()).append(" попереду");
        stats.byCourse().forEach((course, c) -> {
            text.append("\n\n📘 <b>").append(escape(shortCourse(course))).append("</b>");
            Integer coursePercent = c.onTimePercent();
            if (coursePercent != null) {
                text.append("  ").append(bar(coursePercent)).append(' ').append(coursePercent).append('%');
            }
            text.append('\n').append(counts(c));
        });
        text.append("\n\n<i>«Здано» — коли ти тиснеш ✅ під нагадуванням.</i>");
        return text.toString();
    }

    private static String bar(int percent) {
        int filled = Math.round(percent / 10f);
        return "▓".repeat(filled) + "░".repeat(10 - filled);
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

    /** Groups by Kyiv day. Lines are numbered from {@code numberFrom + 1}, or bulleted when it is null. */
    private String byDay(List<Deadline> deadlines, Instant now, Integer numberFrom) {
        boolean numbered = numberFrom != null;
        LocalDate today = now.atZone(KYIV).toLocalDate();
        Map<LocalDate, List<Deadline>> days = sorted(deadlines).stream()
                .collect(Collectors.groupingBy(d -> d.getDueAt().atZone(KYIV).toLocalDate(),
                        LinkedHashMap::new, Collectors.toList()));
        StringBuilder text = new StringBuilder();
        int[] n = {numbered ? numberFrom : 0};
        days.forEach((day, list) -> {
            text.append("\n\n<b>").append(dayName(day, today)).append("</b>");
            for (Deadline d : list) {
                text.append('\n').append(numbered ? ++n[0] + "." : "•").append(" <b>").append(time(d.getDueAt()))
                        .append("</b> · ").append(item(d));
            }
        });
        return text.toString();
    }

    private static String item(Deadline d) {
        return (d.getCourseName() == null ? "" : escape(shortCourse(d.getCourseName())) + " — ") + escape(d.getTitle());
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

    /** Only the non-zero counts: "✅ 1 · ⏰ 1". */
    private static String counts(Stats.Counts c) {
        List<String> parts = new ArrayList<>();
        if (c.onTime() > 0) {
            parts.add("✅ " + c.onTime());
        }
        if (c.late() > 0) {
            parts.add("⏰ " + c.late());
        }
        if (c.overdue() > 0) {
            parts.add("❌ " + c.overdue());
        }
        if (c.upcoming() > 0) {
            parts.add("🔜 " + c.upcoming());
        }
        return String.join(" · ", parts);
    }

    /** "CS201 (2026-2027)" -> "CS201". */
    public static String shortCourse(String course) {
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
