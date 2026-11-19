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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfig.class, CommandHandlerIT.Stubs.class})
class CommandHandlerIT {

    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    static final String URL =
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=7&authtoken=secret777";
    static final long ME = 2001L;
    static final long OTHER = 2002L;
    static final String K = CommandHandler.courseKey("TEST101 (2026-2027)");

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

    private static List<String> callbacks(BotReply reply) {
        return reply.buttons().stream().flatMap(List::stream).map(InlineButton::callbackData).toList();
    }

    @Test
    void groupListsCoursesThatHaveGroupQuizzes() {
        handler.handle(ME, URL);

        BotReply reply = handler.handle(ME, "/group");

        assertThat(reply.buttons().getFirst().getFirst().text()).contains("TEST101").contains("усі групи");
        assertThat(callbacks(reply)).containsExactly("grp:c:" + K);
    }

    @Test
    void choosingCourseShowsGroupNumbersInPlace() {
        handler.handle(ME, URL);

        BotReply reply = handler.handleCallback(ME, "grp:c:" + K);

        assertThat(reply.mode()).isEqualTo(BotReply.Mode.EDIT);
        assertThat(callbacks(reply)).contains("grp:s:" + K + ":1", "grp:s:" + K + ":10", "grp:r:" + K, "grp:list");
    }

    @Test
    void settingGroupHidesOtherGroupsOfThatCourse() {
        handler.handle(ME, URL);

        BotReply reply = handler.handleCallback(ME, "grp:s:" + K + ":3");
        assertThat(reply.buttons().getFirst().getFirst().text()).contains("група 3");
        assertThat(handler.handle(ME, "/deadlines").text()).doesNotContain("Квіз 1");

        handler.handleCallback(ME, "grp:s:" + K + ":2");
        assertThat(handler.handle(ME, "/deadlines").text()).contains("Квіз 1");
    }

    @Test
    void resetShowsAllGroupsAgain() {
        handler.handle(ME, URL);
        handler.handleCallback(ME, "grp:s:" + K + ":3");

        handler.handleCallback(ME, "grp:r:" + K);

        assertThat(handler.handle(ME, "/deadlines").text()).contains("Квіз 1");
    }

    @Test
    void groupWithoutGroupQuizzesSaysSo() {
        fetcher.bodies.put(URI.create(URL),
                StubFeedFetcher.withoutEvent(StubFeedFetcher.fixture(), "1002@teaching.kse.org.ua"));
        handler.handle(ME, URL);

        assertThat(handler.handle(ME, "/group").text()).contains("немає");
    }

    @Test
    void staleCourseButtonIsHandled() {
        handler.handle(ME, URL);

        assertThat(handler.handleCallback(ME, "grp:s:deadbeef:1").mode()).isEqualTo(BotReply.Mode.TOAST);
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
    void quietHoursTimeIsPickedInTwoSteps() {
        handler.handle(ME, URL);

        BotReply start = handler.handleCallback(ME, "qh:edit");
        assertThat(start.mode()).isEqualTo(BotReply.Mode.EDIT);
        assertThat(callbacks(start)).contains("qh:s:22", "qh:s:0");

        BotReply end = handler.handleCallback(ME, "qh:s:22");
        assertThat(callbacks(end)).contains("qh:e:22:7").doesNotContain("qh:e:22:22");

        BotReply done = handler.handleCallback(ME, "qh:e:22:7");
        assertThat(done.mode()).isEqualTo(BotReply.Mode.EDIT);
        AppUser user = users.findByTelegramId(ME).orElseThrow();
        assertThat(user.getQuietStart()).isEqualTo(22);
        assertThat(user.getQuietEnd()).isEqualTo(7);
        assertThat(callbacks(done)).contains("quiet");
        assertThat(done.buttons().stream().flatMap(List::stream).map(InlineButton::text).toList())
                .anyMatch(t -> t.contains("22–07"));
    }

    @Test
    void forgedQuietHoursAreRefused() {
        handler.handle(ME, URL);

        assertThat(handler.handleCallback(ME, "qh:e:22:22").mode()).isEqualTo(BotReply.Mode.TOAST);
        assertThat(handler.handleCallback(ME, "qh:e:99:7").mode()).isEqualTo(BotReply.Mode.TOAST);
        assertThat(users.findByTelegramId(ME).orElseThrow().getQuietStart()).isEqualTo(23);
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
    void deadlinesListHasDoneButtonsIncludingRecentOverdue() {
        handler.handle(ME, URL);
        long overdue = deadline(ME, "1007@teaching.kse.org.ua").getId(); // due 10 Nov, NOW is 19 Nov
        long quiz = deadline(ME, "1002@teaching.kse.org.ua").getId();

        BotReply reply = handler.handle(ME, "/deadlines");

        assertThat(reply.text()).contains("Прострочені").contains("Mind Map 2");
        assertThat(callbacks(reply)).containsExactly("done:" + overdue, "done:" + quiz);
    }

    @Test
    void doneFromListDisappearsFromNextList() {
        handler.handle(ME, URL);
        long overdue = deadline(ME, "1007@teaching.kse.org.ua").getId();

        handler.handleCallback(ME, "done:" + overdue);

        assertThat(handler.handle(ME, "/deadlines").text()).doesNotContain("Mind Map 2");
    }

    @Test
    void doneButtonMarksOwnDeadline() {
        handler.handle(ME, URL);
        long id = deadline(ME, "1003@teaching.kse.org.ua").getId();

        BotReply reply = handler.handleCallback(ME, "done:" + id);

        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.DONE);
        assertThat(reply.mode()).isEqualTo(BotReply.Mode.TOAST);
        assertThat(reply.toggleButtonsFor()).isEqualTo(id);
    }

    @Test
    void undoReopensDeadlineMarkedByMistake() {
        handler.handle(ME, URL);
        long id = deadline(ME, "1003@teaching.kse.org.ua").getId();
        handler.handleCallback(ME, "done:" + id);

        BotReply reply = handler.handleCallback(ME, "undo:" + id);

        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.OPEN);
        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getDoneAt()).isNull();
        assertThat(reply.mode()).isEqualTo(BotReply.Mode.TOAST);
        assertThat(reply.toggleButtonsFor()).isEqualTo(id);
    }

    @Test
    void undoRefusesSomeoneElsesDeadline() {
        handler.handle(ME, URL);
        users.save(new AppUser(OTHER, NOW));
        long myDeadline = deadline(ME, "1003@teaching.kse.org.ua").getId();
        handler.handleCallback(ME, "done:" + myDeadline);

        handler.handleCallback(OTHER, "undo:" + myDeadline);

        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.DONE);
    }

    @Test
    void doneOnDeadlineGoneFromMoodleIsRefused() {
        handler.handle(ME, URL);
        Deadline d = deadline(ME, "1003@teaching.kse.org.ua");
        d.setStatus(DeadlineStatus.GONE);
        deadlines.save(d);

        handler.handleCallback(ME, "done:" + d.getId());

        assertThat(deadline(ME, "1003@teaching.kse.org.ua").getStatus()).isEqualTo(DeadlineStatus.GONE);
    }

    @Test
    void doneClearsPendingSnooze() {
        handler.handle(ME, URL);
        long id = deadline(ME, "1003@teaching.kse.org.ua").getId();
        handler.handleCallback(ME, "snooze:" + id);

        handler.handleCallback(ME, "done:" + id);

        assertThat(jdbc.queryForObject("select count(*) from snooze", Integer.class)).isZero();
    }

    private long deadlineDueIn(java.time.Duration in) {
        handler.handle(ME, URL);
        Deadline d = new Deadline(userId(ME), "soon");
        d.setTitle("Soon");
        d.setDueAt(NOW.plus(in));
        return deadlines.save(d).getId();
    }

    @Test
    void snoozeCloseToDeadlineRemindsTenMinutesBefore() {
        long id = deadlineDueIn(java.time.Duration.ofMinutes(40));

        BotReply reply = handler.handleCallback(ME, "snooze:" + id);

        assertThat(reply.text()).contains("14:30"); // due 12:40Z = 14:40 Kyiv, minus 10 min
    }

    @Test
    void snoozeRightBeforeDeadlineIsRefused() {
        long id = deadlineDueIn(java.time.Duration.ofMinutes(5));

        BotReply reply = handler.handleCallback(ME, "snooze:" + id);

        assertThat(reply.text()).contains("скоро");
        assertThat(jdbc.queryForObject("select count(*) from snooze", Integer.class)).isZero();
    }

    @Test
    void statsSuggestGroupUntilItIsSet() {
        handler.handle(ME, URL);
        assertThat(handler.handle(ME, "/stats").text()).contains("/group");

        handler.handleCallback(ME, "grp:s:" + K + ":2");

        assertThat(handler.handle(ME, "/stats").text()).doesNotContain("/group");
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
