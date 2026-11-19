package ua.dexipua.deadliner.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Guards /api/**: resolves the session cookie to a user (401 otherwise) and, for anything that
 * changes data, requires the X-Deadliner header, which a cross-site form cannot send (CSRF).
 */
@Component
public class SessionFilter extends OncePerRequestFilter {

    public static final String COOKIE = "dl_session";
    public static final String USER_ID = "deadliner.userId";
    static final String CSRF_HEADER = "X-Deadliner";

    private final WebAuthService auth;

    public SessionFilter(WebAuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Long> userId = sessionToken(request).flatMap(auth::userFor);
        if (userId.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
            return;
        }
        boolean changesData = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        if (changesData && request.getHeader(CSRF_HEADER) == null) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "missing " + CSRF_HEADER + " header");
            return;
        }
        request.setAttribute(USER_ID, userId.get());
        chain.doFilter(request, response);
    }

    static Optional<String> sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst();
    }

    static ResponseCookie cookie(String value, boolean secure) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(value.isEmpty() ? Duration.ZERO : WebAuthService.SESSION_TTL)
                .build();
    }

    private static void reject(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
