package ua.dexipua.deadliner.reminder;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuietHoursTest {

    // November: Kyiv = UTC+2
    private static Instant kyiv(String localDateTime) {
        return Instant.parse(localDateTime + ":00Z").minusSeconds(2 * 3600);
    }

    @Test
    void defaultIsElevenToEight() {
        assertThat(QuietHours.DEFAULT.isQuiet(kyiv("2026-11-20T23:30"))).isTrue();
        assertThat(QuietHours.DEFAULT.isQuiet(kyiv("2026-11-20T07:59"))).isTrue();
        assertThat(QuietHours.DEFAULT.isQuiet(kyiv("2026-11-20T08:00"))).isFalse();
        assertThat(QuietHours.DEFAULT.end(kyiv("2026-11-20T23:30"))).isEqualTo(kyiv("2026-11-21T08:00"));
        assertThat(QuietHours.DEFAULT.end(kyiv("2026-11-21T03:00"))).isEqualTo(kyiv("2026-11-21T08:00"));
    }

    @Test
    void customNightWindow() {
        QuietHours owl = new QuietHours(2, 10);

        assertThat(owl.isQuiet(kyiv("2026-11-20T01:59"))).isFalse();
        assertThat(owl.isQuiet(kyiv("2026-11-20T02:00"))).isTrue();
        assertThat(owl.end(kyiv("2026-11-20T05:00"))).isEqualTo(kyiv("2026-11-20T10:00"));
    }

    @Test
    void daytimeWindowForClasses() {
        QuietHours classes = new QuietHours(13, 15);

        assertThat(classes.isQuiet(kyiv("2026-11-20T14:10"))).isTrue();
        assertThat(classes.isQuiet(kyiv("2026-11-20T15:00"))).isFalse();
        assertThat(classes.isQuiet(kyiv("2026-11-20T23:30"))).isFalse();
        assertThat(classes.end(kyiv("2026-11-20T14:10"))).isEqualTo(kyiv("2026-11-20T15:00"));
    }

    @Test
    void emptyOrInvalidWindowIsRejected() {
        assertThatThrownBy(() -> new QuietHours(8, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuietHours(24, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuietHours(-1, 8)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void label() {
        assertThat(QuietHours.DEFAULT.label()).isEqualTo("23:00–08:00");
    }
}
