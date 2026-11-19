package ua.dexipua.deadliner.reminder;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ua.dexipua.deadliner.reminder.ReminderPlanner.SentKey;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Which reminders were already sent; the key includes due_at so a moved deadline is reminded again. */
@Repository
public class SentReminders {

    private final JdbcClient jdbc;

    public SentReminders(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<SentKey> findFor(Collection<Long> deadlineIds) {
        if (deadlineIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("select deadline_id, offset_minutes, due_at from sent_reminder where deadline_id in (:ids)")
                .param("ids", deadlineIds)
                .query((rs, n) -> new SentKey(rs.getLong(1), rs.getInt(2), rs.getTimestamp(3).toInstant()))
                .list());
    }

    public void record(SentKey key, Instant sentAt) {
        jdbc.sql("insert into sent_reminder (deadline_id, offset_minutes, due_at, sent_at) values (?, ?, ?, ?) on conflict do nothing")
                .params(key.deadlineId(), key.offsetMinutes(), Timestamp.from(key.dueAt()), Timestamp.from(sentAt))
                .update();
    }
}
