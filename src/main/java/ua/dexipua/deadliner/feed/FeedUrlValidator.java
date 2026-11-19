package ua.dexipua.deadliner.feed;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Accepts only the KSE Moodle calendar export link, so the bot never fetches arbitrary URLs. */
public final class FeedUrlValidator {

    static final String HOST = "teaching.kse.org.ua";
    static final String PATH = "/calendar/export_execute.php";

    private FeedUrlValidator() {
    }

    public static Optional<URI> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.strip();
        if (text.startsWith("webcal://")) {
            text = "https://" + text.substring("webcal://".length());
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!"https".equals(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || !PATH.equals(uri.getPath())) {
            return Optional.empty();
        }
        Map<String, String> params = queryParams(uri.getRawQuery());
        if (params.getOrDefault("userid", "").isEmpty() || params.getOrDefault("authtoken", "").isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    private static Map<String, String> queryParams(String query) {
        if (query == null) {
            return Map.of();
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> p.length > 1 ? p[1] : "", (a, b) -> a));
    }
}
