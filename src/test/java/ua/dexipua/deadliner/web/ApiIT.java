package ua.dexipua.deadliner.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ua.dexipua.deadliner.TestcontainersConfig;
import ua.dexipua.deadliner.deadline.CourseGroups;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.DeadlineStatus;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ApiIT {

    static final String MATH = "MATH101 (2026-2027)";

    @Autowired MockMvc mvc;
    @Autowired WebAuthService auth;
    @Autowired AppUserRepository users;
    @Autowired DeadlineRepository deadlines;
    @Autowired ReminderSettings settings;
    @Autowired CourseGroups courseGroups;
    @Autowired JdbcTemplate jdbc;

    long me;
    Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate app_user cascade");
        me = users.save(new AppUser(4001L, Instant.now())).getId();
        settings.createDefaultsIfMissing(me);
        String link = auth.issueLink(me);
        session = new Cookie(SessionFilter.COOKIE, auth.exchange(link.substring(link.indexOf("t=") + 2)).orElseThrow());
    }

    private Deadline add(long userId, String course, String title, Duration fromNow) {
        Deadline d = new Deadline(userId, title + fromNow);
        d.setCourseName(course);
        d.setTitle(title);
        d.setDueAt(Instant.now().plus(fromNow));
        return deadlines.save(d);
    }

    private MockHttpServletRequestBuilder change(MockHttpServletRequestBuilder request) {
        return request.cookie(session).header("X-Deadliner", "1");
    }

    @Test
    void deadlinesSplitIntoOverdueAndUpcoming() throws Exception {
        add(me, MATH, "Mind Map 2", Duration.ofDays(-2));
        add(me, "CS201 (2026-2027)", "A3 <b>&", Duration.ofDays(3));
        add(me, MATH, "Far away", Duration.ofDays(20));
        Deadline done = add(me, MATH, "Already done", Duration.ofDays(1));
        done.markDone(Instant.now());
        deadlines.save(done);

        mvc.perform(get("/api/deadlines").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue", hasSize(1)))
                .andExpect(jsonPath("$.overdue[0].title").value("Mind Map 2"))
                .andExpect(jsonPath("$.upcoming", hasSize(2)))
                .andExpect(jsonPath("$.upcoming[0].status").value("DONE"))
                .andExpect(jsonPath("$.upcoming[1].title").value("A3 <b>&"))
                .andExpect(jsonPath("$.upcoming[1].course").value("CS201"));
    }

    @Test
    void otherGroupsAreHidden() throws Exception {
        add(me, MATH, "Квіз · Група 1", Duration.ofDays(1));
        add(me, MATH, "Квіз · Група 7", Duration.ofDays(1));
        courseGroups.set(me, MATH, 1);

        mvc.perform(get("/api/deadlines").cookie(session)).andExpect(jsonPath("$.upcoming", hasSize(1)));
    }

    @Test
    void doneAndUndo() throws Exception {
        long id = add(me, MATH, "Quiz", Duration.ofDays(1)).getId();

        mvc.perform(change(post("/api/deadlines/{id}/done", id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        assertThat(deadlines.findById(id).orElseThrow().getStatus()).isEqualTo(DeadlineStatus.DONE);

        mvc.perform(change(post("/api/deadlines/{id}/undo", id))).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void someoneElsesDeadlineIsNotFound() throws Exception {
        long other = users.save(new AppUser(4002L, Instant.now())).getId();
        long theirs = add(other, MATH, "Theirs", Duration.ofDays(1)).getId();

        mvc.perform(change(post("/api/deadlines/{id}/done", theirs))).andExpect(status().isNotFound());
        assertThat(deadlines.findById(theirs).orElseThrow().getStatus()).isEqualTo(DeadlineStatus.OPEN);
    }

    @Test
    void statsCountPerCourse() throws Exception {
        add(me, MATH, "Overdue", Duration.ofDays(-1));
        add(me, MATH, "Upcoming", Duration.ofDays(1));

        mvc.perform(get("/api/stats").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.overdue").value(1))
                .andExpect(jsonPath("$.total.upcoming").value(1))
                .andExpect(jsonPath("$.total.onTimePercent").value(0))
                .andExpect(jsonPath("$.courses[0].course").value("MATH101"));
    }

    @Test
    void settingsAreSaved() throws Exception {
        mvc.perform(change(put("/api/settings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offsets\":[1440],\"digestEnabled\":false,\"quietHours\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsets", hasSize(1)));

        assertThat(settings.offsets(me)).containsExactly(1440);
        AppUser user = users.findById(me).orElseThrow();
        assertThat(user.isDigestEnabled()).isFalse();
        assertThat(user.isQuietHours()).isFalse();
    }

    @Test
    void unknownOffsetIsRejected() throws Exception {
        mvc.perform(change(put("/api/settings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offsets\":[5],\"digestEnabled\":true,\"quietHours\":true}"))
                .andExpect(status().isBadRequest());
        assertThat(settings.offsets(me)).containsExactlyInAnyOrder(4320, 1440, 180);
    }

    @Test
    void nullOffsetIsRejected() throws Exception {
        mvc.perform(change(put("/api/settings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offsets\":[null],\"digestEnabled\":true,\"quietHours\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingSwitchIsRejectedNotTurnedOff() throws Exception {
        mvc.perform(change(put("/api/settings")).contentType(MediaType.APPLICATION_JSON).content("{\"offsets\":[1440]}"))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(me).orElseThrow().isQuietHours()).isTrue();
    }

    @Test
    void fastParallelSettingsSavesDoNotFail() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 8; i++) {
                String body = i % 2 == 0
                        ? "{\"offsets\":[4320,1440],\"digestEnabled\":true,\"quietHours\":true}"
                        : "{\"offsets\":[180],\"digestEnabled\":false,\"quietHours\":false}";
                results.add(pool.submit(() -> mvc.perform(change(put("/api/settings"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()));
            }
            for (var r : results) {
                assertThat(r.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void groupIsSetAndCleared() throws Exception {
        add(me, MATH, "Квіз · Група 7", Duration.ofDays(1));
        String key = GroupFilter.courseKey(MATH);

        mvc.perform(change(put("/api/groups/{key}", key)).contentType(MediaType.APPLICATION_JSON).content("{\"group\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].group").value(1));
        assertThat(courseGroups.of(me)).containsEntry(MATH, 1);

        mvc.perform(change(put("/api/groups/{key}", key)).contentType(MediaType.APPLICATION_JSON).content("{\"group\":null}"))
                .andExpect(status().isOk());
        assertThat(courseGroups.of(me)).isEmpty();
    }

    @Test
    void badGroupRequestsAreRejected() throws Exception {
        add(me, MATH, "Квіз · Група 7", Duration.ofDays(1));

        mvc.perform(change(put("/api/groups/{key}", "deadbeef")).contentType(MediaType.APPLICATION_JSON).content("{\"group\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(change(put("/api/groups/{key}", GroupFilter.courseKey(MATH))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group\":0}"))
                .andExpect(status().isBadRequest());
    }
}
