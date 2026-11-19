# Deadliner

A Telegram bot that reminds students of the Kyiv School of Economics about their Moodle deadlines.

You paste your Moodle calendar link once. Deadliner then:

- reminds you **3 days, 1 day and 3 hours** before each deadline (you can change this);
- sends a **plan for the week** every Sunday at 19:00;
- shows what is due in the next 7 days with `/deadlines`, plus anything overdue from the last 14 days;
- lets you tap **✅ Done** (under a reminder or in `/deadlines`) so it stops reminding you, **↩️ Undo** if you tapped it by mistake, or **⏰ +1 hour** to be reminded again later;
- hides quizzes of other seminar groups: `/group` lets you pick your group in each course with buttons;
- keeps **quiet hours** (23:00–08:00 by default, or any hours you choose): reminders that fall inside arrive when they end, in one message, unless the deadline is earlier;
- shows `/stats`: handed in on time, late, overdue and upcoming, per course.

**Version 1.2.0** — the Telegram bot and a web dashboard (see [CHANGELOG](CHANGELOG.md)).

### Web dashboard

Send `/web` to the bot: it answers with a one-time link (10 minutes, single use). Opening it logs you in, with no password. The dashboard shows your deadlines (tick ✅ / untick), stats per course, and settings: reminder times, the weekly plan, quiet hours and your group in each course.

| Command | What it does |
|---|---|
| `/start`, `/help` | How to connect your Moodle calendar |
| `/deadlines` | The next 7 days and recent overdue, with ✅ buttons |
| `/stats` | On time / late / overdue / upcoming, per course |
| `/group` | Your seminar group in each course |
| `/settings` | Reminder times, weekly plan, quiet hours |
| `/web` | A login link for the web dashboard |
| `/stop` | Delete all your data |

<!-- TODO: GIF of the bot -->

## How it works

```
Telegram ⇄ Spring Boot app (long polling)
             ├─ sync job, every 30 min: download each user's Moodle iCal feed → PostgreSQL
             ├─ reminder job, every minute: send due reminders (each one exactly once)
             └─ digest job, Sunday 19:00 Kyiv time
```

- **Data source:** Moodle's standard calendar export (iCal). Deadliner never sees your Moodle password.
- **Which events count:** assignments (`… is due`) and quiz/feedback closings (`… закрито` / `… closes`). "Opens" events are skipped.
- **Reminders exactly once:** every sent reminder is stored with the deadline's due date. If a teacher moves a deadline, you are reminded again for the new date.
- **No burst on sign-up:** a reminder fires only near its send time (30-minute window). If you connect 2 hours before a deadline, you do not get the 3-day and 1-day reminders all at once.

## Privacy

- The calendar link contains a personal token. It is stored **encrypted (AES-GCM)**, never logged, and the bot deletes your message with the link from the chat.
- Only deadlines are stored: course, title and due date. Your Telegram name and username are not stored.
- `/stop` deletes all your data.

## Tech

Java 21 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL 16 · Flyway · ical4j · TelegramBots 10 · JUnit 5 · Testcontainers · Docker · GitHub Actions

## Run locally

Requirements: JDK 21, Node 22 and Docker. `run-local.ps1` builds the dashboard once, starts PostgreSQL and the app; the dashboard is at http://127.0.0.1:8080. For frontend work, `cd web; npm run dev` serves it with hot reload on :5173.

```bash
cp .env.example .env              # fill in TELEGRAM_BOT_TOKEN and DEADLINER_FEED_KEY
docker compose up -d              # local PostgreSQL
./mvnw spring-boot:run
```

Generate the encryption key with `openssl rand -base64 32`.

## Tests

```bash
./mvnw verify                     # unit tests + integration tests (needs Docker)
```

The integration tests run against a real PostgreSQL in Testcontainers. They use an anonymised copy of a real KSE feed (`src/test/resources/fixtures/kse-sample.ics`).

## Deploy

See [docs/deploy.md](docs/deploy.md).

## Project layout

```
src/main/java/ua/dexipua/deadliner/
  feed/       link validation, encryption, iCal parsing, sync
  deadline/   deadline entity
  reminder/   reminder planning, reminder + digest jobs, message texts
  bot/        Telegram commands and wiring
  user/       users
docs/superpowers/   design and implementation plan
```
