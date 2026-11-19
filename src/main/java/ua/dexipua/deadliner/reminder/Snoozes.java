package ua.dexipua.deadliner.reminder;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** "Remind me again in an hour": at most one pending snooze per deadline. */
@Repository
public class Snoozes {

    private final JdbcClient jdbc;

    public Snoozes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void snooze(long deadlineId, Instant remindAt) {
        jdbc.sql("insert into snooze (deadline_id, remind_at) values (?, ?) on conflict (deadline_id) do update set remind_at = excluded.remind_at")
                .params(deadlineId, Timestamp.from(remindAt))
                .update();
    }

    public Set<Long> dueAmong(Collection<Long> deadlineIds, Instant now) {
        if (deadlineIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("select deadline_id from snooze where deadline_id in (:ids) and remind_at <= :now")
                .param("ids", deadlineIds)
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .list());
    }

    public void delete(Collection<Long> deadlineIds) {
        if (!deadlineIds.isEmpty()) {
            jdbc.sql("delete from snooze where deadline_id in (:ids)").param("ids", deadlineIds).update();
        }
    }
}
