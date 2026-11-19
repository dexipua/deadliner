# Deadliner Phase 1 (Telegram bot) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A deployed Telegram bot that lets KSE students connect their Moodle iCal feed and get deadline reminders and a weekly digest.

**Architecture:** One Spring Boot app, packaged by feature (`user`, `feed`, `deadline`, `reminder`, `bot`). Two scheduled jobs (sync every 30 min, reminders every minute) over PostgreSQL. The bot uses long polling, so no public URL is needed in Phase 1.

**Tech Stack:** Java 21, Spring Boot 3.4.x, Maven, PostgreSQL 16, Flyway, Spring Data JPA, ical4j 4.x, `org.telegram:telegrambots-springboot-longpolling-starter` + `telegrambots-client` 8.x, JUnit 5, AssertJ, Testcontainers, Docker Compose, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-19-deadliner-design.md`

## Global Constraints

- Store times in UTC (`Instant` / `timestamptz`); show them in `Europe/Kyiv`.
- iCal URLs are encrypted at rest (AES-GCM, key from env `DEADLINER_FEED_KEY`), never logged, never echoed back to the user.
- Accepted feed URL: `https`, host `teaching.kse.org.ua`, path `/calendar/export_execute.php`.
- Default reminder offsets: 4320, 1440, 180 minutes. Digest: Sunday 19:00 Kyiv.
- Only future items missing from the feed become `GONE`. Past items are never changed by sync.
- `sent_reminder` is unique on `(deadline_id, offset_minutes, due_at)`.
- One feed per user; a new link replaces the old one.
- Secrets only in env vars: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `DEADLINER_FEED_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`. `.env` is git-ignored.
- Bot texts in Ukrainian.
- Way of working: Claude writes the code, Artem reviews. Claude adds nothing beyond this plan without asking first. Each task ends with a short note for Artem: what was built, why, what to check, 1–2 tips.

## Review Focus

1. **Connecting with deadlines already close:** a fresh user whose deadline is in 2 hours must get no burst of the 3-day and 1-day reminders. A reminder fires only if its send time is within the last 30 minutes (grace window, see ledger ruling), so at most the smallest offset fires right after connecting. Pinned in Task 6.
2. **Expired or wrong link:** Moodle answers a bad token with an HTML login page and HTTP 200, not with an error. The parser must reject non-iCal bodies, and the user must get a clear message. Pinned in Tasks 4 and 5.
3. **DST change in Kyiv** (last Sunday of March and October): the digest still goes out at 19:00 local time, and displayed due times are correct. Pinned in Task 7.
4. **User blocked the bot:** Telegram returns 403. The bot must stop sending to that user (mark them inactive), and the job must keep running for everyone else. Pinned in Task 8.
5. **Pasted link with spaces, a newline or `webcal://`:** trim it, accept `webcal://` as `https://`, and reject anything else with a hint. Pinned in Task 3.

---

### Task 0: Verify Moodle export and set up accounts (Artem, manual, ~1 h)

- [ ] **Step 1:** In KSE Moodle, open Calendar → Export calendar. Choose "All events" and "Recent and next 60 days", then copy the URL. Expected: a URL on `/calendar/export_execute.php` with `userid` and `authtoken`. **If export is missing, stop and rethink the project.**
- [ ] **Step 2:** Download the feed once and look at the `VEVENT`s. Note how assignment-due and quiz-close events differ from "opens" events (`CATEGORIES`, the `UID` format, or the `SUMMARY` wording). Write the filter rule into the spec.
- [ ] **Step 3:** Make an anonymised copy (fake course names, titles and UIDs; keep the structure) as `src/test/resources/fixtures/kse-sample.ics`. Never commit a real token.
- [ ] **Step 4:** Create the bot with @BotFather and keep the token. Choose a folder for the repository, then run `git init` and create a private GitHub repo `dexipua/deadliner`.

### Task 1: Project skeleton, schema, CI

**Files:**
- Create: `pom.xml`, `src/main/java/ua/dexipua/deadliner/DeadlinerApplication.java`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__init.sql`, `compose.yaml` (postgres for local dev), `.gitignore`, `.env.example`, `.github/workflows/ci.yml`
- Test: `src/test/java/ua/dexipua/deadliner/ApplicationIT.java`, `src/test/java/ua/dexipua/deadliner/TestcontainersConfig.java`

**Interfaces:**
- Produces tables: `app_user(id bigserial, telegram_id bigint unique not null, username text, first_name text, active boolean default true, digest_enabled boolean default true, created_at timestamptz)`, `calendar_feed(id, user_id unique fk, url_encrypted bytea, last_synced_at, last_status text, consecutive_failures int default 0)`, `deadline(id, user_id fk, external_uid text, course_name text, title text, due_at timestamptz, url text, status text check in ('OPEN','DONE','GONE'), done_at timestamptz, unique(user_id, external_uid))`, `reminder_setting(user_id fk, offset_minutes int, primary key(user_id, offset_minutes))`, `sent_reminder(deadline_id fk, offset_minutes int, due_at timestamptz, sent_at timestamptz, primary key(deadline_id, offset_minutes, due_at))`. All FKs to `app_user` are `on delete cascade`.
- Produces `TestcontainersConfig` (`@ServiceConnection` PostgreSQLContainer `postgres:16`) for all `*IT` tests.

- [ ] **Step 1:** Write `ApplicationIT`: the context loads against Testcontainers Postgres, and Flyway applied V1 (`select count(*) from flyway_schema_history where success` == 1).
- [ ] **Step 2:** Run `./mvnw verify`. Expected: FAIL (no app yet).
- [ ] **Step 3:** Add `pom.xml` (Spring Boot 3.4.x parent; web is not needed, so use `spring-boot-starter`, data-jpa, validation, flyway-core + flyway-database-postgresql, postgresql, the telegrambots starter + client, ical4j, testcontainers postgresql + junit-jupiter, spring-boot-testcontainers), the app class, the yml (`spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`) and V1.
- [ ] **Step 4:** Run `./mvnw verify`. Expected: PASS.
- [ ] **Step 5:** CI: on push and PR, set up JDK 21 (temurin), then `./mvnw -B verify`.
- [ ] **Step 6:** Commit `chore: project skeleton, schema, CI`.

### Task 2: Feed URL encryption

**Files:** Create `feed/FeedUrlCipher.java`. Test `feed/FeedUrlCipherTest.java`.

**Interfaces:** Produces `FeedUrlCipher(String base64Key)` with `byte[] encrypt(String url)` and `String decrypt(byte[] data)`. The key is 32 bytes from `DEADLINER_FEED_KEY`; the output format is `iv(12) || ciphertext+tag`.

- [ ] **Step 1:** Tests: `roundTrip` (decrypt(encrypt(u)) == u); `sameInputDifferentCiphertext` (two encrypts differ); `tamperedDataFails` (flipping one byte → `IllegalStateException`); `wrongKeyLengthRejected` (16-byte key → `IllegalArgumentException` at construction).
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement with JCA `AES/GCM/NoPadding`, a 128-bit tag and a random IV from `SecureRandom`.
- [ ] **Step 4:** Run. Expected: PASS. Commit `feat: encrypt feed URLs at rest`.

### Task 3: Feed URL validation

**Files:** Create `feed/FeedUrlValidator.java`. Test `feed/FeedUrlValidatorTest.java`.

**Interfaces:** Produces `static Optional<URI> normalize(String raw)`: trims the input, turns `webcal://` into `https://`, and accepts only the host and path from Global Constraints with non-empty `userid` and `authtoken` query params.

- [ ] **Step 1:** Tests: accepts the canonical URL; accepts it with leading or trailing spaces and a newline; accepts `webcal://…` and returns `https://…`; rejects `http://`; rejects another host (`teaching.kse.org.ua.evil.com`); rejects a missing `authtoken`; rejects plain text.
- [ ] **Step 2:** Run, see it fail, implement, see it pass. Commit `feat: validate Moodle feed URL`.

### Task 4: iCal parsing and deadline filter

**Files:** Create `feed/IcalParser.java`, `feed/ParsedDeadline.java` (record). Test `feed/IcalParserTest.java` using `fixtures/kse-sample.ics` from Task 0.

**Interfaces:** Produces `record ParsedDeadline(String uid, String courseName, String title, Instant dueAt, String url)` and `List<ParsedDeadline> parse(InputStream body) throws InvalidFeedException` (checked, in `feed`).

- [ ] **Step 1:** Tests: the fixture returns exactly the expected number of deadlines (counted by hand in Task 0), all "opens" events are excluded, and `dueAt` of one known event equals its `DTSTART` as an `Instant`. An HTML body (`<!DOCTYPE html>…login…`) throws `InvalidFeedException`. An empty `VCALENDAR` returns an empty list.
- [ ] **Step 2:** Run, see it fail. Implement with ical4j `CalendarBuilder`, applying the filter rule written in Task 0. The course name comes from `CATEGORIES` if present, otherwise from the summary prefix. Run, see it pass. Commit `feat: parse Moodle iCal feed`.

### Task 5: Feed connect and sync

**Files:** Create `user/AppUser.java`, `user/AppUserRepository.java`, `feed/CalendarFeed.java`, `feed/CalendarFeedRepository.java`, `deadline/Deadline.java`, `deadline/DeadlineStatus.java`, `deadline/DeadlineRepository.java`, `feed/FeedFetcher.java`, `feed/FeedSyncService.java`, `feed/FeedSyncJob.java`. Test `feed/FeedSyncServiceIT.java`.

**Interfaces:**
- Consumes `FeedUrlCipher`, `FeedUrlValidator`, `IcalParser`.
- Produces `FeedFetcher.fetch(URI) -> InputStream`: an interface with an `HttpClient` implementation (10 s timeout, User-Agent `Deadliner`). Tests stub it.
- Produces `FeedSyncService.connect(long telegramId, String rawUrl) -> ConnectResult` (sealed: `Connected(int count, Optional<Deadline> nearest)`, `InvalidUrl`, `FeedUnreachable`). It creates the user if missing, replaces an existing feed, runs a first sync, and creates the default reminder settings for new users.
- Produces `FeedSyncService.sync(long feedId, Instant now) -> SyncOutcome` (`Ok(added, updated, gone)`, `Failed(consecutiveFailures)`).
- Produces `FeedSyncJob`: `@Scheduled(fixedDelay = 30 min)` iterates active users' feeds one at a time.

- [ ] **Step 1:** IT tests:
  - connect with a valid stubbed feed → `Connected` with the right count, and the stored `url_encrypted` does not contain the plain URL;
  - re-sync with one changed `dueAt` → updated;
  - a future item removed from the feed → `GONE`;
  - a past item removed → unchanged;
  - an HTML body → `Failed(1)` and `last_status` set;
  - on exactly the 3rd consecutive failure `FeedSyncJob` sends the user one "посилання на Moodle перестало працювати, надішли нове" message via `Notifier` (not on the 4th);
  - a second connect replaces the feed (still one row).
- [ ] **Step 2:** Run, see it fail, implement, see it pass. Commit `feat: connect and sync Moodle feeds`.

### Task 6: Reminder planning and sending

**Files:** Create `reminder/ReminderPlanner.java`, `reminder/DueReminder.java`, `reminder/ReminderJob.java`, `reminder/SentReminderRepository.java`. Test `reminder/ReminderPlannerTest.java` (pure), `reminder/ReminderJobIT.java`.

**Interfaces:**
- Produces `List<DueReminder> plan(List<Deadline> open, Set<Integer> offsets, Set<SentKey> alreadySent, Instant lastRun, Instant now)`. A reminder is due when `dueAt - offset` falls in `(lastRun, now]`, the deadline is `OPEN`, `dueAt > now`, and `(id, offset, dueAt)` is not in `alreadySent`.
- Produces `Notifier` (an interface in `bot`, implemented in Task 8): `void send(long telegramId, String text, List<InlineButton> buttons)`, which throws `UserBlockedException`.
- `ReminderJob`: `@Scheduled(fixedDelay = 60 s)`. It merges several reminders for one user into one message, records `sent_reminder` rows in the same transaction as the send attempt, and stores `lastRun` in memory, starting at app start time.

- [ ] **Step 1:** Planner tests:
  - the 1-day offset fires once inside the window;
  - it does not fire again on the next run;
  - **a deadline connected 2 h before due fires no 3-day or 1-day reminder** (those times are before `lastRun`);
  - a moved `dueAt` fires again;
  - a `DONE` deadline never fires.
- [ ] **Step 2:** IT: the job with a fake `Notifier` sends one merged message for two due reminders.
- [ ] **Step 3:** Run, see it fail, implement, see it pass. Commit `feat: deadline reminders`.

### Task 7: Weekly digest and Kyiv time formatting

**Files:** Create `reminder/DigestJob.java`, `reminder/MessageFormatter.java`. Test `reminder/MessageFormatterTest.java`, `reminder/DigestScheduleTest.java`.

**Interfaces:**
- Produces `MessageFormatter.reminder(List<Deadline>) -> String`, `.digest(List<Deadline> nextWeek) -> String`, and `.list(List<Deadline>) -> String`. All three group by Kyiv date and use the format `Пн 23.11, 23:59 — <course>: <title>`.
- `DigestJob`: `@Scheduled(cron = "0 0 19 * * SUN", zone = "Europe/Kyiv")` sends to users with `digest_enabled` and at least one deadline.

- [ ] **Step 1:** Tests:
  - the formatter shows `2027-03-28T20:59:00Z` as `Нд 28.03, 23:59` (summer time UTC+3);
  - it shows `2026-12-01T21:59:00Z` as `Вт 01.12, 23:59` (UTC+2);
  - an empty digest returns no message;
  - the digest cron resolves to 19:00 Kyiv on both sides of the DST change (check with `CronExpression.next` in `Europe/Kyiv`).
- [ ] **Step 2:** Run, see it fail, implement, see it pass. Commit `feat: weekly digest`.

### Task 8: Telegram bot commands

**Files:** Create `bot/DeadlinerBot.java` (the `LongPollingSingleThreadUpdateConsumer` + `SpringLongPollingBot`), `bot/TelegramNotifier.java`, `bot/CommandHandler.java`, `bot/InlineButton.java`, `bot/UserBlockedException.java`, `bot/Texts.java`. Test `bot/CommandHandlerTest.java`.

**Interfaces:**
- Consumes `FeedSyncService.connect`, `DeadlineRepository`, `MessageFormatter`.
- Produces `CommandHandler.handle(long telegramId, String text) -> BotReply` and `handleCallback(long telegramId, String data) -> BotReply`, so the logic is testable without Telegram. `BotReply(String text, List<InlineButton> buttons, boolean deleteUserMessage)`.
- Behaviour:
  - `/start` shows the intro, the privacy note and how to get the link, then waits for the link;
  - any message that `FeedUrlValidator` accepts → `connect`, with `deleteUserMessage = true`;
  - `/deadlines` shows the next 7 days;
  - `/settings` toggles the offsets 3d/1d/3h and the digest with inline buttons;
  - `/stop` asks to confirm, then deletes the user (cascade);
  - the callback `done:<deadlineId>` marks DONE only if the deadline belongs to the caller.
- `TelegramNotifier` maps Telegram 403 to `UserBlockedException`. `ReminderJob` and `DigestJob` catch it, set `active = false`, and continue.

- [ ] **Step 1:** Tests:
  - an unknown text without a link gives a hint;
  - a valid link → a `Connected` reply with the count and `deleteUserMessage`;
  - `done:` with another user's deadline id → refused;
  - `/stop` + confirm leaves the user gone;
  - a 403 during a job → the user becomes inactive and the next user is still notified (IT with a fake notifier that throws for the first user).
- [ ] **Step 2:** Run, see it fail, implement, see it pass.
- [ ] **Step 3:** Manual check with the real bot token locally: `/start` → paste the link → `/deadlines` → wait for a reminder. To trigger one in a few minutes, temporarily add a 5-minute offset via SQL.
- [ ] **Step 4:** Commit `feat: Telegram bot commands`.

### Task 9: Deploy and README

**Files:** Create `Dockerfile` (multi-stage, temurin 21 JRE), `compose.prod.yaml` (app + postgres + a daily `pg_dump` backup container), `README.md`, `docs/deploy.md`.

- [ ] **Step 1:** `docker compose -f compose.prod.yaml up -d` locally with `.env`. Expected: the bot answers `/start`.
- [ ] **Step 2:** Pick hosting (spec §7: a cheap VPS is the default). Deploy and check the bot in Telegram. Check that a backup file appears after the first run.
- [ ] **Step 3:** README sections: what it is, a GIF of the bot, how it works (diagram from the spec), privacy, run locally, roadmap (Phase 2 web). Commit `docs: README and deploy`.
- [ ] **Step 4:** Invite 5–10 classmates. Note the feedback for the Phase 2 plan.
