package ua.dexipua.deadliner.reminder;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** How long before a deadline each user wants to be reminded, in minutes. */
@Repository
public class ReminderSettings {

    /** 3 days, 1 day, 3 hours. */
    public static final List<Integer> DEFAULT_OFFSETS = List.of(4320, 1440, 180);

    private final JdbcClient jdbc;

    public ReminderSettings(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void createDefaultsIfMissing(long userId) {
        for (int offset : DEFAULT_OFFSETS) {
            jdbc.sql("insert into reminder_setting (user_id, offset_minutes) values (?, ?) on conflict do nothing")
                    .params(userId, offset)
                    .update();
        }
    }

    public Set<Integer> offsets(long userId) {
        return new TreeSet<>(jdbc.sql("select offset_minutes from reminder_setting where user_id = ?")
                .param(userId)
                .query(Integer.class)
                .list());
    }
}
