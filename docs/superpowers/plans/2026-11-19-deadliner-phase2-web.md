# Deadliner Phase 2 (web dashboard) Implementation Plan

> Execution: superpowers:executing-plans (native), one final review.

**Goal:** A web dashboard served by the bot's Spring Boot app, logged into via a one-time link from `/web`.
**Spec:** `docs/superpowers/specs/2026-11-19-deadliner-design.md` §5.5 and §9.
**Tech:** spring-boot-starter-web, Flyway V6, React 19 + TypeScript + Vite in `web/`, built into `src/main/resources/static`.

## Global constraints
- Login token: 32 random bytes, base64url, 10 min, single use; DB keeps SHA-256 only. Session: 32 random bytes, 30 days, cookie `dl_session`, HttpOnly, SameSite=Lax, Path=/, Secure iff base URL starts with https.
- `/api/**` without a valid session → 401 JSON. Unknown or someone else's deadline → 404.
- Mutating API calls require header `X-Deadliner: 1` (CSRF guard).
- All times in API are ISO-8601 UTC; the UI shows Kyiv time.
- Group filter, GONE rules and snooze cleanup must be the same as in the bot (`DeadlineActions`).

## Review focus
1. Reusing or racing a login link (double click, expired, someone forwards it after use).
2. Session cookie on http://localhost vs https in production.
3. API returning another user's data by guessing ids.
4. Settings payload with junk (unknown offsets, group 0 / 999, huge lists).
5. Frontend showing Moodle titles with HTML characters.

## Tasks
1. **DeadlineActions** — extract done/undo/snooze from CommandHandler; bot tests keep passing. Unit/IT: owner check, GONE refused, snooze cleanup.
2. **Web server + login** — starter-web, V6 `web_login_token`, `web_session`; `WebAuthService.issueLink(userId)`, `exchange(token) -> Optional<String sessionToken>`, `userFor(sessionToken)`, `logout`; `/web` bot command; `GET /auth?t=` (redirect `/` or `/?login=expired`); `SessionFilter`. IT: link works once, expired fails, cookie flags, 401 without cookie.
3. **API** — `GET /api/me`, `GET /api/deadlines`, `POST /api/deadlines/{id}/done|undo`, `GET /api/stats`, `PUT /api/settings`, `PUT /api/groups/{courseKey}`, `POST /api/logout`. IT with MockMvc per endpoint incl. 404 for foreign ids and 400 for junk.
4. **Frontend** — Vite app: login screen (how to get a link), Deadlines, Stats, Settings tabs; `npm run build` → static. Typecheck + build in CI.
5. **Packaging** — Dockerfile node stage, CI job for web, run-local builds web once, README.
