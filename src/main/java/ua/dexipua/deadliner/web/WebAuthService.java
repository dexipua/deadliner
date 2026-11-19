package ua.dexipua.deadliner.web;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Login without passwords: the bot sends a one-time link (/web), opening it starts a browser session.
 * Tokens are random; only their SHA-256 hashes are stored, so a database leak gives no working links.
 */
@Service
public class WebAuthService {

    static final Duration LINK_TTL = Duration.ofMinutes(10);
    static final Duration SESSION_TTL = Duration.ofDays(30);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public WebAuthService(JdbcClient jdbc, Clock clock, @Value("${deadliner.web.base-url}") String baseUrl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!isHttps()) {
            LoggerFactory.getLogger(WebAuthService.class).warn(
                    "Web dashboard URL is {} (not https). Fine on your own computer; in production set DEADLINER_WEB_URL.",
                    this.baseUrl);
        }
    }

    public boolean isHttps() {
        return baseUrl.startsWith("https://");
    }

    public String issueLink(long userId) {
        String token = newToken();
        jdbc.sql("insert into web_login_token (token_hash, user_id, expires_at) values (?, ?, ?)")
                .params(hash(token), userId, Timestamp.from(clock.instant().plus(LINK_TTL)))
                .update();
        return baseUrl + "/auth?t=" + token;
    }

    /** Uses the link up (atomically, so a double click cannot open two sessions) and starts a session. */
    @Transactional
    public Optional<String> exchange(String linkToken) {
        if (linkToken == null || linkToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<Long> userId = jdbc.sql("""
                        update web_login_token set used_at = ?
                        where token_hash = ? and used_at is null and expires_at > ?
                        returning user_id""")
                .params(Timestamp.from(now), hash(linkToken), Timestamp.from(now))
                .query(Long.class)
                .optional();
        return userId.map(id -> {
            String session = newToken();
            jdbc.sql("insert into web_session (token_hash, user_id, expires_at) values (?, ?, ?)")
                    .params(hash(session), id, Timestamp.from(now.plus(SESSION_TTL)))
                    .update();
            return session;
        });
    }

    public Optional<Long> userFor(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("select user_id from web_session where token_hash = ? and expires_at > ?")
                .params(hash(sessionToken), Timestamp.from(clock.instant()))
                .query(Long.class)
                .optional();
    }

    public void logout(String sessionToken) {
        jdbc.sql("delete from web_session where token_hash = ?").param(hash(sessionToken)).update();
    }

    /** "Log out everywhere": ends every dashboard session of this user; returns how many there were. */
    public int logoutEverywhere(long userId) {
        return jdbc.sql("delete from web_session where user_id = ?").param(userId).update();
    }

    /** Daily: forget links and sessions that expired more than a day ago. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Kyiv")
    public void cleanUp() {
        Timestamp before = Timestamp.from(clock.instant().minus(Duration.ofDays(1)));
        jdbc.sql("delete from web_login_token where expires_at < ?").param(before).update();
        jdbc.sql("delete from web_session where expires_at < ?").param(before).update();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
