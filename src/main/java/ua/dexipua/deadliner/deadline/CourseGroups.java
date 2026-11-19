package ua.dexipua.deadliner.deadline;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/** The student's seminar group in each course; a course without an entry shows all groups. */
@Repository
public class CourseGroups {

    private final JdbcClient jdbc;

    public CourseGroups(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Integer> of(long userId) {
        Map<String, Integer> groups = new HashMap<>();
        jdbc.sql("select course_name, group_number from course_group where user_id = ?")
                .param(userId)
                .query(rs -> {
                    groups.put(rs.getString(1), rs.getInt(2));
                });
        return groups;
    }

    public void set(long userId, String course, int group) {
        jdbc.sql("""
                        insert into course_group (user_id, course_name, group_number) values (?, ?, ?)
                        on conflict (user_id, course_name) do update set group_number = excluded.group_number""")
                .params(userId, course, group)
                .update();
    }

    public void clear(long userId, String course) {
        jdbc.sql("delete from course_group where user_id = ? and course_name = ?").params(userId, course).update();
    }
}
