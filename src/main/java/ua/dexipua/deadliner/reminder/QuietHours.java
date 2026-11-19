package ua.dexipua.deadliner.reminder;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** No reminders between 23:00 and 08:00 Kyiv time. */
final class QuietHours {

    static final LocalTime START = LocalTime.of(23, 0);
    static final LocalTime END = LocalTime.of(8, 0);

    private QuietHours() {
    }

    static boolean isQuiet(Instant at) {
        LocalTime t = at.atZone(MessageFormatter.KYIV).toLocalTime();
        return !t.isBefore(START) || t.isBefore(END);
    }

    /** The 08:00 that ends the quiet period containing {@code at}. */
    static Instant end(Instant at) {
        ZonedDateTime kyiv = at.atZone(MessageFormatter.KYIV);
        ZonedDateTime end = kyiv.with(END).withSecond(0).withNano(0);
        if (!kyiv.toLocalTime().isBefore(START)) {
            end = end.plusDays(1);
        }
        return end.toInstant();
    }
}
