package ua.dexipua.deadliner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliedAllMigrations() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where not success", Integer.class);
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(failed).isZero();
        assertThat(applied).isPositive();
    }
}
