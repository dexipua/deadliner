package ua.dexipua.deadliner.bot;

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
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.support.StubFeedFetcher;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfig.class, CommandHandlerIT.Stubs.class})
class CommandHandlerIT {

    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    static final String URL =
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=7&authtoken=secret777";
    static final long ME = 2001L;
    static final long OTHER = 2002L;

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

    @Autowired CommandHandler handler;
    @Autowired StubFeedFetcher fetcher;
    @Autowired AppUserRepository users;
    @Autowired DeadlineRepository deadlines;
    @Autowired ReminderSettings settings;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate app_user cascade");
        fetcher.bodies.clear();
        fetcher.bodies.put(URI.create(URL), StubFeedFetcher.fixture());
    }

    private long userId(long telegramId) {
        return users.findByTelegramId(telegramId).orElseThrow().getId();
    }

    private Deadline deadline(long telegramId, String uid) {
        return deadlines.findByUserId(userId(telegramId)).stream()
                .filter(d -> d.getExternalUid().equals(uid)).findFirst().orElseThrow();
    }

    @Test
    void startIsShortAndOffersHelpButton() {
        BotReply reply = handler.handle(ME, "/start");

        assertThat(reply.buttons().getFirst().getFirst().callbackData()).isEqualTo("help:link");
    }

    @Test
    void helpButtonExplainsHowToGetTheLinkEvenBeforeConnecting() {
        BotReply reply = handler.handleCallback(ME, "help:link");

        assertThat(reply.text()).contains("Експорт").contains("Отримати URL календаря");
        assertThat(reply.mode()).isEqualTo(BotReply.Mode.SEND);
    }

    @Test
    void randomTextGetsAHint() {
        BotReply reply = handler.handle(ME, "привіт");

        assertThat(reply.text()).contains("/help");
        assertThat(reply.deleteUserMessage()).isFalse();
    }

    @Test
    void validLinkConnectsAndDeletesTheMessageWithToken() {
        BotReply reply = handler.handle(ME, URL);

        assertThat(reply.text()).contains("4").contains("Квіз 1");
        assertThat(reply.deleteUserMessage()).isTrue();
    }

    @Test
    void expiredLinkIsReportedAndStillDeleted() {
        fetcher.bodies.put(URI.create(URL), "Invalid authentication");

        BotReply reply = handler.handle(ME, URL);

        assertThat(reply.text()).contains("Не вдалося");
        assertThat(reply.deleteUserMessage()).isTrue();
    }

    @Test
    void invalidLinkWithTokenIsStillDeleted() {
        BotReply reply = handler.handle(ME, "ось: " + URL.replace("https://", "http://"));

        assertThat(reply.text()).contains("/help");
        assertThat(reply.deleteUserMessage()).isTrue();
    }

    @Test
    void connectSuggestsSettingGroupWhenFeedHasGroupQuizzes() {
        assertThat(handler.handle(ME, URL).text()).contains("/group");
    }

    @Test
    void groupHidesOtherGroupsQuizzes() {
        handler.handle(ME, URL);

        assertThat(handler.handle(ME, "/group 3").text()).contains("3");
        assertThat(handler.handle(ME, "/deadlines").text()).doesNotContain("Квіз 1");

        handler.handle(ME, "/group 2");
        assertThat(handler.handle(ME, "/deadlines").text()).contains("Квіз 1");
    }

    @Test
    void groupWithoutNumberShowsAllAgain() {
        handler.handle(ME, URL);
        handler.handle(ME, "/group 3");

        handler.handle(ME, "/group");

        assertThat(handler.handle(ME, "/deadlines").text()).contains("Квіз 1");
    }

    @Test
    void groupWithGarbageGetsAHint() {
        handler.handle(ME, URL);

        assertThat(handler.handle(ME, "/group five").text()).contains("/group 5");
    }

    @Test
    void snoozeButtonSchedulesReminderInAnHour() {
        handler.handle(ME, URL);
        long id = deadline(ME, "1003@teaching.kse.org.ua").getId();

        BotReply reply = handler.handleCallback(ME, "snooze:" + id);

        assertThat(reply.text()).contains("15:00"); // NOW 12:00Z = 14:00 Kyiv, +1 h
        assertThat(reply.mode()).isEqualTo(BotReply.Mode.TOAST);
        assertThat(jdbc.queryForObject("select count(*) from snooze where deadline_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void snoozeRefusesSomeoneElsesDeadline() {
        handler.handle(ME, URL);
        users.save(new AppUser(OTHER, NOW));
        long myDeadline = deadline(ME, "1003@teaching.kse.org.ua").getId();

        handler.handleCallback(OTHER, "snooze:" + myDeadline);

        assertThat(jdbc.queryForObject("select count(*) from snooze", Integer.class)).isZero();
    }

    @Test
    void quietHoursCanBeTurnedOff() {
        handler.handle(ME, URL);

        handler.handleCallback(ME, "quiet");

        assertThat(users.findByTelegramId(ME).orElseThrow().isQuietHours()).isFalse();
    }

    @Test
    void statsShowsCountsPerCourse() {
        handler.handle(ME, URL);
        handler.handleCallback(ME, "done:" + deadline(ME, "1003@teaching.kse.org.ua").getId());

        String text = handler.handle(ME, "/stats").text();

        assertThat(text).contains("TEST201").contains("TEST101");
    }

    @Test
    void deadlinesBeforeConnectingAsksToConnect() {
        assertThat(handler.handle(ME, "/deadlines").text()).contains("/help");
    }

    @Test
    void deadlinesShowsTheNextSevenDays() {
        handler.handle(ME, URL);

        String text = handler.handle(ME, "/deadlines").text();

        assertThat(text).contains("Квіз 1").doesNotContain("Assignment 1");
    }

    @Test
    void doneButtonMarksOwnDeadline() {
        handler.handle(ME, URL);
        long id = deadline(ME, "1003@teaching.kse.org.ua").getId();

        BotReply reply = handler.handleCallback(ME, "done:" + id);

        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.DONE);
        assertThat(reply.mode()).isEqualTo(BotReply.Mode.TOAST);
        assertThat(reply.clearButtonsFor()).isEqualTo(id);
    }

    @Test
    void doneButtonRefusesSomeoneElsesDeadline() {
        handler.handle(ME, URL);
        users.save(new AppUser(OTHER, NOW));
        long myDeadline = deadline(ME, "1003@teaching.kse.org.ua").getId();

        handler.handleCallback(OTHER, "done:" + myDeadline);

        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.OPEN);
    }

    @Test
    void settingsButtonTogglesAnOffset() {
        handler.handle(ME, URL);

        BotReply reply = handler.handleCallback(ME, "offset:180");

        assertThat(settings.offsets(userId(ME))).containsExactlyInAnyOrder(4320, 1440);
        assertThat(reply.mode()).isEqualTo(BotReply.Mode.EDIT);
    }

    @Test
    void stopNeedsConfirmationThenDeletesEverything() {
        handler.handle(ME, URL);

        BotReply ask = handler.handle(ME, "/stop");
        assertThat(users.findByTelegramId(ME)).isPresent();
        handler.handleCallback(ME, ask.buttons().getFirst().getFirst().callbackData());

        assertThat(users.findByTelegramId(ME)).isEmpty();
        assertThat(deadlines.count()).isZero();
    }

    @Test
    void stopCanBeCancelled() {
        handler.handle(ME, URL);

        BotReply reply = handler.handleCallback(ME, "stop:cancel");

        assertThat(reply.mode()).isEqualTo(BotReply.Mode.EDIT);
        assertThat(users.findByTelegramId(ME)).isPresent();
    }

    @Test
    void writingAgainReactivatesBlockedUser() {
        handler.handle(ME, URL);
        AppUser user = users.findByTelegramId(ME).orElseThrow();
        user.setActive(false);
        users.save(user);

        handler.handle(ME, "/deadlines");

        assertThat(users.findByTelegramId(ME).orElseThrow().isActive()).isTrue();
    }
}
