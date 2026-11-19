package ua.dexipua.deadliner.feed;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcalParserTest {

    private final IcalParser parser = new IcalParser();

    private List<ParsedDeadline> parseFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/kse-sample.ics")) {
            return parser.parse(in);
        }
    }

    @Test
    void keepsOnlyDueAndCloseEvents() throws Exception {
        assertThat(parseFixture()).extracting(ParsedDeadline::uid).containsExactlyInAnyOrder(
                "1002@teaching.kse.org.ua", "1003@teaching.kse.org.ua", "1004@teaching.kse.org.ua",
                "1006@teaching.kse.org.ua", "1007@teaching.kse.org.ua");
    }

    @Test
    void mapsFieldsOfAssignment() throws Exception {
        ParsedDeadline assignment = parseFixture().stream()
                .filter(d -> d.uid().equals("1003@teaching.kse.org.ua")).findFirst().orElseThrow();

        assertThat(assignment.title()).isEqualTo("Assignment 1 - Parsing & Sorting");
        assertThat(assignment.courseName()).isEqualTo("TEST201 (2026-2027)");
        assertThat(assignment.dueAt()).isEqualTo(Instant.parse("2026-11-27T21:59:00Z"));
    }

    @Test
    void stripsUkrainianCloseSuffix() throws Exception {
        assertThat(parseFixture()).extracting(ParsedDeadline::title)
                .contains("Квіз 1 · Заняття 3 · Група 2", "Final Feedback", "Lab 4");
    }

    @Test
    void rejectsHtmlLoginPage() {
        InputStream html = stream("Invalid authentication");
        assertThatThrownBy(() -> parser.parse(html)).isInstanceOf(InvalidFeedException.class);
    }

    @Test
    void rejectsHtmlDocument() {
        InputStream html = stream("<!DOCTYPE html><html><body>login</body></html>");
        assertThatThrownBy(() -> parser.parse(html)).isInstanceOf(InvalidFeedException.class);
    }

    @Test
    void emptyCalendarGivesEmptyList() throws Exception {
        String empty = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Moodle//EN\r\nEND:VCALENDAR\r\n";
        assertThat(parser.parse(stream(empty))).isEmpty();
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
