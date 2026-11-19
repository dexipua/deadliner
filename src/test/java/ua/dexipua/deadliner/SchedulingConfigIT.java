package ua.dexipua.deadliner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/** A slow feed sync must not block the reminder job: each job needs its own scheduler thread. */
@SpringBootTest(properties = "deadliner.scheduling.enabled=true")
@Import(TestcontainersConfig.class)
class SchedulingConfigIT {

    @Autowired
    ThreadPoolTaskScheduler scheduler;

    @Test
    void schedulerHasAThreadPerJob() {
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThanOrEqualTo(3);
    }
}
