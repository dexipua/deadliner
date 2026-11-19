package ua.dexipua.deadliner.support;

import ua.dexipua.deadliner.feed.FeedFetcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Test double: serves feed bodies from memory. */
public class StubFeedFetcher implements FeedFetcher {

    public final Map<URI, String> bodies = new HashMap<>();

    @Override
    public InputStream fetch(URI uri) throws IOException {
        String body = bodies.get(uri);
        if (body == null) {
            throw new IOException("no stub for " + uri);
        }
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    public static String fixture() {
        try (InputStream in = StubFeedFetcher.class.getResourceAsStream("/fixtures/kse-sample.ics")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String withoutEvent(String ics, String uid) {
        String result = ics.replaceAll(
                "(?s)BEGIN:VEVENT\r\nUID:" + Pattern.quote(uid) + "\r\n.*?END:VEVENT\r\n", "");
        if (result.equals(ics)) {
            throw new IllegalArgumentException("event not found: " + uid);
        }
        return result;
    }
}
