package ua.dexipua.deadliner.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final WebAuthService auth;
    private final String botUsername;

    public AuthController(WebAuthService auth, @Value("${deadliner.telegram.username:}") String botUsername) {
        this.auth = auth;
        this.botUsername = botUsername;
    }

    /** The link from /web lands here: start a session and go to the dashboard. */
    @GetMapping("/auth")
    public ResponseEntity<Void> login(@RequestParam(name = "t", required = false) String token) {
        Optional<String> session = auth.exchange(token);
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/?login=expired").build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/")
                .header(HttpHeaders.SET_COOKIE, SessionFilter.cookie(session.get(), auth.isHttps()).toString())
                .build();
    }

    /** What the login screen needs before anyone is logged in. */
    @GetMapping("/public/info")
    public Map<String, String> info() {
        return Map.of("botUsername", botUsername);
    }

    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SessionFilter.sessionToken(request).ifPresent(auth::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionFilter.cookie("", auth.isHttps()).toString())
                .build();
    }
}
