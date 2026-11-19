package ua.dexipua.deadliner.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ua.dexipua.deadliner.TestcontainersConfig;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.support.RecordingNotifier;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ReminderJobIT {

    static final Instant NOW = Instant.parse("2026-11-19T21:59:30Z");
    static final Instant DUE_TOMORROW = Instant.parse("2026-11-20T21:59:00Z");

    @Autowired ReminderJob job;
    @Autowired AppUserRepository users;
    @Autowired DeadlineRepository deadlines;
    @Autowired ReminderSettings settings;
    @Autowired RecordingNotifier notifier;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate app_user cascade");
        notifier.reset();
    }

    private AppUser userWithTwoDeadlinesDueTomorrow(long telegramId) {
        AppUser user = users.save(new AppUser(telegramId, NOW));
        settings.createDefaultsIfMissing(user.getId());
        for (String title : new String[] {"Essay", "Quiz"}) {
            Deadline d = new Deadline(user.getId(), title + telegramId);
            d.setTitle(title);
            d.setCourseName("ENG121 (2026-2027)");
            d.setDueAt(DUE_TOMORROW);
            deadlines.save(d);
        }
        return user;
    }

    @Test
    void twoDueRemindersForOneUserAreMergedIntoOneMessage() {
        userWithTwoDeadlinesDueTomorrow(500L);

        job.runOnce(NOW);

        assertThat(notifier.sent).hasSize(1);
        assertThat(notifier.sent.getFirst().text()).contains("Essay").contains("Quiz");
        assertThat(notifier.sent.getFirst().buttons()).hasSize(2);
        assertThat(notifier.sent.getFirst().buttons().getFirst().callbackData()).startsWith("done:");
    }

    @Test
    void nextRunDoesNotRepeatSentReminders() {
        userWithTwoDeadlinesDueTomorrow(500L);

        job.runOnce(NOW);
        job.runOnce(NOW.plusSeconds(60));

        assertThat(notifier.sent).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from sent_reminder", Integer.class)).isEqualTo(2);
    }

    @Test
    void otherGroupsQuizIsNotReminded() {
        AppUser user = users.save(new AppUser(700L, NOW));
        user.setSeminarGroup(5);
        users.save(user);
        settings.createDefaultsIfMissing(user.getId());
        for (String title : new String[] {"Квіз 8 · Група 5", "Квіз 8 · Група 7"}) {
            Deadline d = new Deadline(user.getId(), title);
            d.setTitle(title);
            d.setDueAt(DUE_TOMORROW);
            deadlines.save(d);
        }

        job.runOnce(NOW);

        assertThat(notifier.sent.getFirst().text()).contains("Група 5").doesNotContain("Група 7");
    }

    @Test
    void userDeletedMidJobDoesNotStopRemindersForOthers() {
        AppUser leaving = userWithTwoDeadlinesDueTomorrow(500L);
        userWithTwoDeadlinesDueTomorrow(600L);
        notifier.beforeSend = telegramId -> {
            if (telegramId == 500L) {
                users.deleteById(leaving.getId()); // the user hits /stop while the job is sending
            }
        };

        job.runOnce(NOW);

        assertThat(notifier.sent).extracting(RecordingNotifier.Sent::telegramId).contains(600L);
    }

    @Test
    void blockedUserIsDeactivatedAndOthersStillGetReminders() {
        userWithTwoDeadlinesDueTomorrow(500L);
        userWithTwoDeadlinesDueTomorrow(600L);
        notifier.blocked.add(500L);

        job.runOnce(NOW);

        assertThat(users.findByTelegramId(500L).orElseThrow().isActive()).isFalse();
        assertThat(notifier.sent).extracting(RecordingNotifier.Sent::telegramId).containsExactly(600L);
    }
}
