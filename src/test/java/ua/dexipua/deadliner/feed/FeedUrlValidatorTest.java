package ua.dexipua.deadliner.feed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class FeedUrlValidatorTest {

    private static final String VALID =
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=42&authtoken=abc123&preset_what=all&preset_time=recentupcoming";

    @Test
    void acceptsCanonicalUrl() {
        assertThat(FeedUrlValidator.normalize(VALID)).contains(URI.create(VALID));
    }

    @Test
    void trimsSpacesAndNewlines() {
        assertThat(FeedUrlValidator.normalize("  " + VALID + "\n")).contains(URI.create(VALID));
    }

    @Test
    void turnsWebcalIntoHttps() {
        String webcal = VALID.replace("https://", "webcal://");
        assertThat(FeedUrlValidator.normalize(webcal)).contains(URI.create(VALID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://teaching.kse.org.ua/calendar/export_execute.php?userid=42&authtoken=abc123",
            "https://teaching.kse.org.ua.evil.com/calendar/export_execute.php?userid=42&authtoken=abc123",
            "https://teaching.kse.org.ua/calendar/export.php?userid=42&authtoken=abc123",
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=42",
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=&authtoken=abc123",
            "привіт, ось моє посилання",
            ""
    })
    void rejectsAnythingElse(String raw) {
        assertThat(FeedUrlValidator.normalize(raw)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(FeedUrlValidator.normalize(null)).isEmpty();
    }
}
