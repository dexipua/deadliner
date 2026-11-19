package ua.dexipua.deadliner.feed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import ua.dexipua.deadliner.TestcontainersConfig;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.DeadlineStatus;
import ua.dexipua.deadliner.feed.FeedSyncService.ConnectResult;
import ua.dexipua.deadliner.feed.FeedSyncService.SyncOutcome;
import ua.dexipua.deadliner.support.RecordingNotifier;
import ua.dexipua.deadliner.support.StubFeedFetcher;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfig.class, FeedSyncServiceIT.Stubs.class})
class FeedSyncServiceIT {

    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    static final String URL =
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=7&authtoken=secret777";
    static final long TG = 1001L;

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        @Primary
        StubFeedFetcher stubFeedFetcher() {
            return new StubFeedFetcher();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired FeedSyncService service;
    @Autowired FeedSyncJob job;
    @Autowired StubFeedFetcher fetcher;
    @Autowired RecordingNotifier notifier;
    @Autowired DeadlineRepository deadlines;
    @Autowired AppUserRepository users;
    @Autowired CalendarFeedRepository feeds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate app_user cascade");
        fetcher.bodies.clear();
        fetcher.bodies.put(URI.create(URL), StubFeedFetcher.fixture());
        notifier.reset();
    }

    private long userId() {
        return users.findByTelegramId(TG).orElseThrow().getId();
    }

    private Deadline deadline(String uid) {
        return deadlines.findByUserId(userId()).stream()
                .filter(d -> d.getExternalUid().equals(uid)).findFirst().orElseThrow();
    }

    private long feedId() {
        return feeds.findByUserId(userId()).orElseThrow().getId();
    }

    @Test
    void connectStoresFeedAndReportsUpcomingDeadlines() {
        ConnectResult result = service.connect(TG, "  " + URL + "\n");

        assertThat(result).isInstanceOf(ConnectResult.Connected.class);
        ConnectResult.Connected connected = (ConnectResult.Connected) result;
        assertThat(connected.count()).isEqualTo(4);
        assertThat(connected.nearest()).get().extracting(Deadline::getExternalUid)
                .isEqualTo("1002@teaching.kse.org.ua");
        assertThat(deadlines.findByUserId(userId())).hasSize(5);
    }

    @Test
    void connectEncryptsUrlAtRest() {
        service.connect(TG, URL);

        byte[] stored = jdbc.queryForObject("select url_encrypted from calendar_feed", byte[].class);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("secret777");
    }

    @Test
    void connectCreatesDefaultReminderOffsets() {
        service.connect(TG, URL);

        assertThat(jdbc.queryForList("select offset_minutes from reminder_setting", Integer.class))
                .containsExactlyInAnyOrder(4320, 1440, 180);
    }

    @Test
    void connectRejectsForeignUrlAndSavesNothing() {
        ConnectResult result = service.connect(TG, "https://example.com/cal.ics");

        assertThat(result).isInstanceOf(ConnectResult.InvalidUrl.class);
        assertThat(feeds.count()).isZero();
    }

    @Test
    void connectWithExpiredTokenSavesNothing() {
        fetcher.bodies.put(URI.create(URL), "Invalid authentication");

        ConnectResult result = service.connect(TG, URL);

        assertThat(result).isInstanceOf(ConnectResult.FeedUnreachable.class);
        assertThat(feeds.count()).isZero();
    }

    @Test
    void secondConnectReplacesFeed() {
        String other = URL.replace("secret777", "secret888");
        fetcher.bodies.put(URI.create(other), StubFeedFetcher.fixture());

        service.connect(TG, URL);
        service.connect(TG, other);

        assertThat(feeds.count()).isEqualTo(1);
    }

    @Test
    void resyncUpdatesMovedDeadline() {
        service.connect(TG, URL);
        fetcher.bodies.put(URI.create(URL), StubFeedFetcher.fixture()
                .replace("DTSTART:20261127T215900Z", "DTSTART:20261129T215900Z"));

        SyncOutcome outcome = service.sync(feedId(), NOW);

        assertThat(outcome).isEqualTo(new SyncOutcome.Ok(0, 1, 0));
        assertThat(deadline("1003@teaching.kse.org.ua").getDueAt())
                .isEqualTo(Instant.parse("2026-11-29T21:59:00Z"));
    }

    @Test
    void futureDeadlineMissingFromFeedBecomesGone() {
        service.connect(TG, URL);
        fetcher.bodies.put(URI.create(URL),
                StubFeedFetcher.withoutEvent(StubFeedFetcher.fixture(), "1003@teaching.kse.org.ua"));

        service.sync(feedId(), NOW);

        assertThat(deadline("1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.GONE);
    }

    @Test
    void pastDeadlineMissingFromFeedIsKept() {
        service.connect(TG, URL);
        fetcher.bodies.put(URI.create(URL),
                StubFeedFetcher.withoutEvent(StubFeedFetcher.fixture(), "1007@teaching.kse.org.ua"));

        service.sync(feedId(), NOW);

        assertThat(deadline("1007@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.OPEN);
    }

    @Test
    void htmlBodyOnSyncCountsAsFailure() {
        service.connect(TG, URL);
        fetcher.bodies.put(URI.create(URL), "<!DOCTYPE html><html>login</html>");

        SyncOutcome outcome = service.sync(feedId(), NOW);

        assertThat(outcome).isEqualTo(new SyncOutcome.Failed(1));
        assertThat(feeds.findById(feedId()).orElseThrow().getLastStatus()).isNotBlank();
    }

    @Test
    void jobWarnsUserExactlyOnceOnThirdFailure() {
        service.connect(TG, URL);
        fetcher.bodies.put(URI.create(URL), "Invalid authentication");

        for (int i = 0; i < 4; i++) {
            job.runOnce(NOW);
        }

        assertThat(notifier.sent).hasSize(1);
        assertThat(notifier.sent.getFirst().telegramId()).isEqualTo(TG);
    }

    @Test
    void jobMarksBlockedUserInactive() {
        service.connect(TG, URL);
        fetcher.bodies.put(URI.create(URL), "Invalid authentication");
        notifier.blocked.add(TG);

        for (int i = 0; i < 3; i++) {
            job.runOnce(NOW);
        }

        assertThat(users.findByTelegramId(TG).orElseThrow().isActive()).isFalse();
    }
}
