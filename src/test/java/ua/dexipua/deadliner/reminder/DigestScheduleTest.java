package ua.dexipua.deadliner.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DigestScheduleTest {

    private final CronExpression cron = CronExpression.parse(DigestJob.CRON);

    @Test
    void scheduleRunsInKyivTime() throws Exception {
        var scheduled = DigestJob.class.getMethod("run")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo(DigestJob.CRON);
        assertThat(scheduled.zone()).isEqualTo("Europe/Kyiv");
    }

    @Test
    void firesAtSevenPmKyivOnSpringDstSunday() {
        ZonedDateTime saturday = ZonedDateTime.of(2027, 3, 27, 12, 0, 0, 0, MessageFormatter.KYIV);

        ZonedDateTime next = cron.next(saturday);

        assertThat(next).isEqualTo(ZonedDateTime.of(2027, 3, 28, 19, 0, 0, 0, MessageFormatter.KYIV));
        assertThat(next.getOffset().getTotalSeconds()).isEqualTo(3 * 3600);
    }

    @Test
    void firesAtSevenPmKyivOnAutumnDstSunday() {
        ZonedDateTime saturday = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, MessageFormatter.KYIV);

        ZonedDateTime next = cron.next(saturday);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 25, 19, 0, 0, 0, MessageFormatter.KYIV));
        assertThat(next.getOffset().getTotalSeconds()).isEqualTo(2 * 3600);
    }
}
