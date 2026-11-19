package ua.dexipua.deadliner.feed;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Downloads a feed with a hard limit on the whole request (not only headers) and on body size,
 * so a stalled or huge response cannot hang the sync job.
 */
@Component
public class HttpFeedFetcher implements FeedFetcher {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    // a student's 60-day export is a few KB
    private static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;

    private final Duration timeout;
    private final int maxBytes;
    private final HttpClient client;

    @Autowired
    public HttpFeedFetcher() {
        this(DEFAULT_TIMEOUT, DEFAULT_MAX_BYTES);
    }

    HttpFeedFetcher(Duration timeout, int maxBytes) {
        this.timeout = timeout;
        this.maxBytes = maxBytes;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public InputStream fetch(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", "Deadliner")
                .GET()
                .build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        try {
            HttpResponse<InputStream> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status >= 400 && status < 500) {
                response.body().close();
                throw new FeedRejectedException("Moodle answered HTTP " + status);
            }
            if (status != 200) {
                response.body().close();
                throw new IOException("Moodle answered HTTP " + status);
            }
            return new ByteArrayInputStream(readLimited(response.body()));
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new IOException("Moodle did not answer in " + timeout.toSeconds() + " s");
        } catch (ExecutionException e) {
            throw new IOException("Could not reach Moodle: " + e.getCause().getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching feed");
        }
    }

    // reading runs on a separate thread so the same timeout covers a body that stalls midway
    private byte[] readLimited(InputStream body) throws IOException, InterruptedException, TimeoutException {
        var reader = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try (body) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = body.read(buffer)) != -1) {
                    if (out.size() + n > maxBytes) {
                        throw new IOException("Feed is larger than " + maxBytes + " bytes");
                    }
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        try {
            return reader.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause().getMessage());
        } catch (TimeoutException e) {
            reader.cancel(true);
            closeQuietly(body);
            throw e;
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // already giving up on this response
        }
    }
}
