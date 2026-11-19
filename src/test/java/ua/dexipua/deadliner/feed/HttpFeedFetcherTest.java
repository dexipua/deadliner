package ua.dexipua.deadliner.feed;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpFeedFetcherTest {

    private HttpServer server;
    private final HttpFeedFetcher fetcher = new HttpFeedFetcher(Duration.ofMillis(500), 1024);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> {
            byte[] body = "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/stall", ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.getResponseBody().write('B');
            ex.getResponseBody().flush();
            sleep(5_000); // headers sent, body never finishes
            ex.close();
        });
        server.createContext("/huge", ex -> {
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(new byte[4096]);
            }
        });
        server.createContext("/gone", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    void readsBody() throws IOException {
        assertThat(fetcher.fetch(uri("/ok")).readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("BEGIN:VCALENDAR");
    }

    @Test
    void stalledBodyTimesOutInsteadOfHangingTheJob() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> fetcher.fetch(uri("/stall"))).isInstanceOf(IOException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void oversizedBodyIsRejected() {
        assertThatThrownBy(() -> fetcher.fetch(uri("/huge"))).isInstanceOf(IOException.class);
    }

    @Test
    void clientErrorMeansTheLinkIsRejected() {
        assertThatThrownBy(() -> fetcher.fetch(uri("/gone"))).isInstanceOf(FeedRejectedException.class);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
