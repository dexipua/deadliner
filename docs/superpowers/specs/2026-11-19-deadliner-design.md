# Deadliner — design

Date: 2026-11-19 · Author: Artem (dexipua) with Claude · Status: draft, awaiting approval

## 1. Intent and why

Deadliner reminds KSE students about Moodle deadlines in Telegram and shows them on a small web dashboard.

Why it exists (the user's words and goals):
- **Portfolio:** all of Artem's strong Java projects are team projects. Deadliner is the first **solo** full-stack project with a live demo, aimed at a Java / full-stack internship (target: summer 2027, Grid Dynamics and similar).
- **Real users:** classmates at KSE, from day one. "N real users" on the CV matters more than features.
- **Discipline:** Artem's own weak spot. The tool should help him (and others) hand work in on time.

## 2. Goals, non-goals, anti-goals

**Goals (MVP)**
1. A student connects their Moodle calendar through the Telegram bot in under 1 minute.
2. Reminders arrive at configurable offsets (default: 3 days, 1 day, 3 hours before) and a weekly digest on Sunday evening.
3. A web dashboard (login with Telegram) shows upcoming deadlines as a list and a calendar, lets the user mark items done, change reminder settings, and see simple stats (done on time / overdue per course).
4. Deployed, public, with a live link in the README.
5. Clean engineering that an interviewer can read: layered Spring Boot, tests (unit + Testcontainers integration), CI, Docker.

**Non-goals (MVP)**
- Submitting assignments, reading grades, or anything that needs the Moodle password or Moodle web-service tokens.
- Other universities, other calendar sources (Google Calendar, etc.). The design should not block them, but no code for them.
- Mobile app, email reminders, i18n beyond Ukrainian + English UI strings.
- Group features (shared deadlines between friends).

**Anti-goals (failure even if it works)**
- Storing anyone's Moodle password, or leaking a user's iCal link (it contains a personal auth token). A leak would expose their calendar.
- Spamming: duplicate reminders, reminders for already-done items, reminders at 3 a.m.
- A half-finished project. Scope must fit ~20 h/week total of Artem's free time, of which this project gets ~5–7 h/week; MVP should be shippable in **~5–6 weeks**.
- Code Artem cannot explain in an interview. He writes or reviews every part; Claude pairs.

## 3. Constraints

- **Data source:** Moodle's standard calendar export (`/calendar/export.php` → "Export calendar" → iCal URL with `userid` + `authtoken`). **Not yet verified that KSE Moodle (teaching.kse.org.ua) has export enabled** — this is the first task of the plan; if disabled, the project needs a rethink before any code.
- Moodle export only covers a window (e.g. "recent and next 60 days") and only events that teachers put in the calendar (assignment due dates, quizzes). Items not in the calendar are invisible to Deadliner; the UI should say so.
- Telegram Login Widget requires an HTTPS domain registered with BotFather (`/setdomain`).
- Free / cheap hosting; one person operates it.
- Stack must show Java strength: Java 21, Spring Boot 3.
- **Not CS201 coursework** — the CS201 AI ban does not apply. Do not reuse graded CS201 code.
- Ukrainian time zone (Europe/Kyiv) for all user-facing times; store UTC.

## 4. Approaches considered

| | Approach | Trade-off |
|---|---|---|
| A | Bot only, one Spring Boot service | ~2–3 weeks, all Java; no web page to show |
| **B (picked)** | **Bot + React web dashboard, one Spring Boot backend** | ~2× the work, more visible in a portfolio; risk of dragging on → mitigated by phasing (bot first, web second) |
| C | Python bot | Dropped: the project exists to show Java |

Artem picked **B**. To keep the "half-finished" anti-goal away, B is built in two phases: Phase 1 is the full bot (usable by classmates on its own), Phase 2 adds the web dashboard on the same backend.

## 5. Design

### 5.1 Architecture

```
Telegram ⇄ Spring Boot backend (one deployable)
             ├─ bot module: long polling, commands
             ├─ sync job: every 30 min, fetch each user's iCal feed → upsert deadlines
             ├─ reminder job: every minute, send due reminders (idempotent)
             ├─ REST API (/api/**) for the web app, auth = Telegram login → JWT
             └─ PostgreSQL (Flyway migrations)
React + TypeScript SPA (Vite) → served by Nginx, calls /api
Docker Compose: postgres, backend, nginx (+ static frontend)
```

One backend, not microservices: simpler to run, test and explain.

### 5.2 Data model (first cut)

- `app_user` — id, telegram_id (unique), telegram_username, first_name, timezone (default Europe/Kyiv), created_at, digest_enabled, quiet_hours (default 23:00–08:00).
- `calendar_feed` — id, user_id, url_encrypted (AES-GCM, key from env), last_synced_at, last_status, consecutive_failures.
- `deadline` — id, user_id, feed_id, external_uid (iCal UID), course_name, title, due_at (UTC), url, status (OPEN / DONE / GONE), done_at, unique(user_id, external_uid).
- `reminder_setting` — user_id, offset_minutes (e.g. 4320, 1440, 180).
- `sent_reminder` — deadline_id, offset_minutes, sent_at, unique(deadline_id, offset_minutes) → guarantees no duplicates.

### 5.3 Bot (Phase 1)

- `/start` → explains what the bot does and how to get the Moodle export link (short step-by-step with a screenshot), then waits for the link.
- User pastes the link → backend validates: HTTPS, host = teaching.kse.org.ua, path `/calendar/export_execute.php`, then test-fetches it. On success: "Found N deadlines, the nearest is …". The message containing the link is deleted from the chat after saving (it contains a token).
- `/deadlines` — next 7 days, grouped by day.
- `/done` — inline buttons on each reminder message: "✅ Done", "⏰ Snooze 1 h".
- `/settings` — reminder offsets, digest on/off, quiet hours.
- `/stop` — deletes the user and all their data, confirms.
- Weekly digest: Sunday 19:00 Kyiv time.
- Reminders falling in quiet hours are sent at the end of quiet hours, unless the deadline would pass first.

### 5.4 Sync

- Every 30 minutes, for each feed: fetch, parse with a Java iCal library (ical4j), upsert by `external_uid`. Items missing from the feed → status GONE (not deleted, keeps stats honest). Changed due dates update `due_at`; reminders already sent for the old time are kept, new offsets get recomputed.
- Failures: back off; after N consecutive failures tell the user once ("your Moodle link stopped working, send a new one").
- Be polite to Moodle: stagger requests, one at a time, timeout, User-Agent "Deadliner".

### 5.5 Web dashboard (Phase 2)

- Login with Telegram Login Widget → backend verifies the hash with the bot token → issues a short-lived JWT (httpOnly cookie).
- Pages: Upcoming (list), Calendar (month view), Stats (on time vs late per course, streak), Settings (same as bot).
- React 19 + TypeScript + Vite; UI library MUI (Artem already uses it).

### 5.6 Security and privacy

- iCal URLs encrypted at rest; never logged; never returned by the API (only "connected / last synced").
- Data minimisation: only what's listed above. `/stop` and a "delete my data" button in Settings.
- A short privacy note in the bot `/start` and on the site.
- Secrets (bot token, encryption key, DB password) only in env vars; `.env` in `.gitignore`; GitHub secret scanning on.

### 5.7 Quality

- Tests: unit tests for parsing, reminder scheduling and quiet-hours logic; integration tests with Testcontainers (PostgreSQL); a recorded sample iCal file as a fixture (with fake data, not a real student's).
- GitHub Actions: build + test on every push; Docker image build.
- README: what it is, screenshot/GIF, live link, architecture diagram, how to run locally, "what I'd do next".

### 5.8 Deployment

- One small VPS or Fly.io with a domain (needed for Telegram login). Docker Compose, Nginx with HTTPS (Let's Encrypt). Daily DB backup.

## 6. Phases and rough timeline (~5–7 h/week)

1. **Week 0 (1 h):** verify KSE Moodle iCal export works; create the bot with BotFather.
2. **Weeks 1–3 — Phase 1, bot:** skeleton + DB, link onboarding, sync, reminders, settings, `/stop`, tests, deploy. **Launch to 5–10 classmates.**
3. **Weeks 4–6 — Phase 2, web:** Telegram login, API, React pages, stats.
4. Then: README polish, LinkedIn post, CV line.

## 7. Decisions made by Claude (Artem can override)

- One Spring Boot backend (no microservices); long polling (no public webhook needed during Phase 1).
- ical4j for parsing; Flyway; PostgreSQL; MUI for UI; Vite.
- Default reminder offsets 3 d / 1 d / 3 h; digest Sunday 19:00; quiet hours 23:00–08:00.
- Sync every 30 min.
- Mark-as-done lives in Deadliner only (cannot write back to Moodle).
- Hosting: decide in Week 0 between a cheap VPS and Fly.io; the domain is needed only for Phase 2.

### Settled after the builder check (Claude's calls)
- **What counts as a deadline:** assignment due dates and quiz *close* events; skip "opens" events, attendance and site/personal events. Exact filter (Moodle event type in the iCal `CATEGORIES`/UID, or title keywords) is fixed in Week 0 from a real KSE feed, which also becomes the anonymised test fixture.
- **GONE rule:** only *future* items missing from the feed become GONE. Past items that leave the export window are kept as-is, so stats keep their history.
- **Overdue** = due time passed and the user did not mark it Done in Deadliner (Moodle does not expose submission status). The UI says this plainly.
- **Export range:** onboarding asks for "All events" + "Recent and next 60 days"; the bot warns if the feed looks shorter.
- **Moved deadline:** `sent_reminder` is unique on (deadline_id, offset_minutes, due_at), so a moved deadline gets its reminders again for the new time.
- One feed per user; a new link replaces the old. Reminders that pile up at the end of quiet hours are merged into one message. Done/Snooze are inline buttons only.

### How we work (Artem's decision, 2026-11-19)
- **Claude writes the code, Artem reviews and steers.** Artem already has fully self-written projects; here the skill being practised is working with an AI pair.
- **Keep it simple.** Claude builds only what is in this doc or what Artem asks for; anything extra is proposed in one line first, never just added.
- Each step is small (one reviewable change), ends with: what was built, why this way, what to look at in review, and one or two tips.
- Before each feature Claude asks how Artem wants it to look/behave when the doc leaves it open.

### Cut from MVP to keep it simple (move to "later")
Snooze button, quiet hours, month calendar view, streaks. MVP web = Upcoming list, Stats (on time vs overdue per course), Settings.

## 8. Left to the builder

Package layout, exact API shapes, UI layout details, library versions, retry/back-off numbers, how the snooze is stored.
