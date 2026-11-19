package ua.dexipua.deadliner.reminder;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * A student's quiet hours in Kyiv time, on whole hours. The window may cross midnight (23–08)
 * or sit inside one day (13–15, during classes). Reminders that fall inside it wait for its end.
 */
public record QuietHours(int startHour, int endHour) {

    public static final QuietHours DEFAULT = new QuietHours(23, 8);

    public QuietHours {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23 || startHour == endHour) {
            throw new IllegalArgumentException("Quiet hours must be two different hours 0-23, got " + startHour + "-" + endHour);
        }
    }

    private boolean crossesMidnight() {
        return startHour > endHour;
    }

    public boolean isQuiet(Instant at) {
        int hour = at.atZone(MessageFormatter.KYIV).getHour();
        return crossesMidnight() ? hour >= startHour || hour < endHour : hour >= startHour && hour < endHour;
    }

    /** The end of the quiet period that contains {@code at}. */
    public Instant end(Instant at) {
        ZonedDateTime kyiv = at.atZone(MessageFormatter.KYIV);
        ZonedDateTime end = kyiv.with(LocalTime.of(endHour, 0));
        if (crossesMidnight() && kyiv.getHour() >= startHour) {
            end = end.plusDays(1);
        }
        return end.toInstant();
    }

    /** The start of the quiet period that contains {@code at}. */
    public Instant start(Instant at) {
        ZonedDateTime kyiv = at.atZone(MessageFormatter.KYIV);
        ZonedDateTime start = kyiv.with(LocalTime.of(startHour, 0));
        if (crossesMidnight() && kyiv.getHour() < endHour) {
            start = start.minusDays(1);
        }
        return start.toInstant();
    }

    /** "23:00–08:00". */
    public String label() {
        return String.format("%02d:00–%02d:00", startHour, endHour);
    }

    /** "23–08", for a button. */
    public String shortLabel() {
        return String.format("%02d–%02d", startHour, endHour);
    }
}
