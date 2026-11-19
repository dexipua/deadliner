package ua.dexipua.deadliner.feed;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtStart;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads a Moodle calendar export and keeps only real deadlines.
 * Moodle marks them by the summary suffix (verified on the KSE feed, Moodle 4.2):
 * "is due" for assignments, "закрито"/"closes" for quizzes and feedback.
 */
public class IcalParser {

    private static final List<String> DEADLINE_SUFFIXES = List.of(" is due", " закрито", " closes");

    public List<ParsedDeadline> parse(InputStream body) throws InvalidFeedException {
        Calendar calendar;
        try {
            calendar = new CalendarBuilder().build(body);
        } catch (ParserException | IOException e) {
            throw new InvalidFeedException("Feed is not an iCal calendar", e);
        }
        List<ParsedDeadline> result = new ArrayList<>();
        for (VEvent event : calendar.<VEvent>getComponents(Component.VEVENT)) {
            toDeadline(event).ifPresent(result::add);
        }
        return result;
    }

    private Optional<ParsedDeadline> toDeadline(VEvent event) {
        String summary = unescapeHtml(value(event, Property.SUMMARY));
        String title = stripDeadlineSuffix(summary);
        String uid = value(event, Property.UID);
        Instant dueAt = dueAt(event);
        if (title == null || uid == null || dueAt == null) {
            return Optional.empty();
        }
        return Optional.of(new ParsedDeadline(uid, value(event, Property.CATEGORIES), title, dueAt, null));
    }

    private static String stripDeadlineSuffix(String summary) {
        if (summary == null) {
            return null;
        }
        for (String suffix : DEADLINE_SUFFIXES) {
            if (summary.endsWith(suffix)) {
                return summary.substring(0, summary.length() - suffix.length()).strip();
            }
        }
        return null;
    }

    private static Instant dueAt(VEvent event) {
        return event.<DtStart<Temporal>>getProperty(Property.DTSTART)
                .map(start -> {
                    Temporal date = start.getDate();
                    try {
                        return Instant.from(date);
                    } catch (DateTimeException allDayEvent) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static String value(VEvent event, String name) {
        return event.getProperty(name).map(Property::getValue).filter(v -> !v.isBlank()).orElse(null);
    }

    // Moodle HTML-escapes summaries before putting them in the feed
    private static String unescapeHtml(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#039;", "'").replace("&#39;", "'").replace("&amp;", "&");
    }
}
