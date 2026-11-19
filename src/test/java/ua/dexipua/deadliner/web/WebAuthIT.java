package ua.dexipua.deadliner.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ua.dexipua.deadliner.TestcontainersConfig;
import ua.dexipua.deadliner.bot.CommandHandler;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class WebAuthIT {

    @Autowired MockMvc mvc;
    @Autowired WebAuthService auth;
    @Autowired AppUserRepository users;
    @Autowired CommandHandler handler;
    @Autowired JdbcTemplate jdbc;

    long userId;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate app_user cascade");
        userId = users.save(new AppUser(3001L, Instant.now())).getId();
    }

    private static String token(String link) {
        return link.substring(link.indexOf("t=") + 2);
    }

    private Cookie login() throws Exception {
        MvcResult result = mvc.perform(get("/auth").param("t", token(auth.issueLink(userId)))).andReturn();
        return result.getResponse().getCookie(SessionFilter.COOKIE);
    }

    @Test
    void linkLogsInOnceAndSetsSafeCookie() throws Exception {
        String t = token(auth.issueLink(userId));

        MvcResult first = mvc.perform(get("/auth").param("t", t))
                .andExpect(status().isFound()).andExpect(header().string("Location", "/")).andReturn();
        String setCookie = first.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains(SessionFilter.COOKIE + "=").contains("HttpOnly").contains("SameSite=Lax")
                .doesNotContain("Secure");

        mvc.perform(get("/auth").param("t", t))
                .andExpect(status().isFound()).andExpect(header().string("Location", "/?login=expired"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void expiredLinkDoesNotLogIn() throws Exception {
        String t = token(auth.issueLink(userId));
        jdbc.update("update web_login_token set expires_at = now() - interval '1 minute'");

        mvc.perform(get("/auth").param("t", t))
                .andExpect(header().string("Location", "/?login=expired"));
    }

    @Test
    void garbageTokenDoesNotLogIn() throws Exception {
        mvc.perform(get("/auth").param("t", "nope")).andExpect(header().string("Location", "/?login=expired"));
    }

    @Test
    void onlyHashesAreStored() {
        String t = token(auth.issueLink(userId));

        String stored = jdbc.queryForObject("select token_hash from web_login_token", String.class);
        assertThat(stored).isNotEqualTo(t).hasSize(64);
    }

    @Test
    void apiNeedsSession() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie(SessionFilter.COOKIE, "forged"))).andExpect(status().isUnauthorized());
    }

    @Test
    void apiWorksWithSession() throws Exception {
        mvc.perform(get("/api/me").cookie(login())).andExpect(status().isOk()).andExpect(jsonPath("$.quietHours").value(true));
    }

    @Test
    void changesNeedTheCsrfHeader() throws Exception {
        Cookie session = login();

        mvc.perform(post("/api/logout").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").cookie(session).header("X-Deadliner", "1")).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionOfDeletedUserIsGone() throws Exception {
        Cookie session = login();
        users.deleteById(userId);

        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void secureCookieOnHttps() {
        assertThat(SessionFilter.cookie("x", true).toString()).contains("Secure");
    }

    @Test
    void botWebCommandSendsLink() {
        assertThat(handler.handle(3001L, "/web").text()).contains("/auth?t=");
        assertThat(handler.handle(3999L, "/web").text()).contains("/help");
    }

    @Test
    void publicInfoNeedsNoLogin() throws Exception {
        mvc.perform(get("/public/info")).andExpect(status().isOk()).andExpect(jsonPath("$.botUsername").exists());
    }
}
