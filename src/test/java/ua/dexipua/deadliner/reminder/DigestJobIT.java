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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class DigestJobIT {

    static final Instant SUNDAY_EVENING = Instant.parse("2026-11-22T17:00:00Z");

    @Autowired DigestJob job;
    @Autowired AppUserRepository users;
    @Autowired DeadlineRepository deadlines;
    @Autowired RecordingNotifier notifier;
    @Autowired ua.dexipua.deadliner.deadline.CourseGroups courseGroups;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate app_user cascade");
        notifier.reset();
    }

    private AppUser user(long telegramId, boolean digest, Duration dueIn) {
        AppUser user = new AppUser(telegramId, SUNDAY_EVENING);
        user.setDigestEnabled(digest);
        user = users.save(user);
        Deadline d = new Deadline(user.getId(), "uid" + telegramId);
        d.setTitle("Task " + telegramId);
        d.setDueAt(SUNDAY_EVENING.plus(dueIn));
        deadlines.save(d);
        return user;
    }

    @Test
    void sendsDigestOnlyToUsersWithDeadlinesThisWeekAndDigestOn() {
        user(1L, true, Duration.ofDays(3));
        user(2L, false, Duration.ofDays(3));
        user(3L, true, Duration.ofDays(10));

        job.runOnce(SUNDAY_EVENING);

        assertThat(notifier.sent).extracting(RecordingNotifier.Sent::telegramId).containsExactly(1L);
        assertThat(notifier.sent.getFirst().text()).contains("Task 1");
    }

    @Test
    void digestSkipsOtherGroups() {
        AppUser user = user(1L, true, Duration.ofDays(3));
        courseGroups.set(user.getId(), "MATH101 (2026-2027)", 5);
        Deadline other = new Deadline(user.getId(), "g7");
        other.setTitle("Квіз · Група 7");
        other.setCourseName("MATH101 (2026-2027)");
        other.setDueAt(SUNDAY_EVENING.plus(Duration.ofDays(2)));
        deadlines.save(other);

        job.runOnce(SUNDAY_EVENING);

        assertThat(notifier.sent.getFirst().text()).doesNotContain("Група 7");
    }

    @Test
    void blockedUserIsDeactivated() {
        user(1L, true, Duration.ofDays(3));
        notifier.blocked.add(1L);

        job.runOnce(SUNDAY_EVENING);

        assertThat(users.findByTelegramId(1L).orElseThrow().isActive()).isFalse();
    }
}
