# Changelog

## 1.3.0 — 2026-11-19

Polish from the code reviews.

- Reminders that would land in quiet hours but are due before they end now come as quiet hours begin (e.g. 23:00), not in the middle of the night; a deadline exactly at 08:00 is reminded the evening before.
- "+1 год" respects quiet hours; "Скасувати" brings back the "+1 год" button in reminders.
- `/logout` ends every web dashboard session; the /web message explains Telegram's in-app browser.
- A dead Moodle link is retried every 6 hours instead of every 30 minutes after the warning.
- Forged settings buttons are ignored; the Moodle host check ignores letter case; deactivating a user no longer overwrites settings saved at the same moment.
- 183 tests.

## 1.2.0 — 2026-11-19

- Quiet hours are now yours to choose: any two whole hours in Kyiv time, across midnight (22–07) or within a day (13–15 during classes). In the bot: /settings → "🕒 Змінити час" (start, then end, edited in place); in the dashboard: "З" and "до" next to the switch. Default stays 23:00–08:00.
- Bot avatar variants in `docs/brand/`.

## 1.1.0 — 2026-11-19

Web dashboard.

- `/web` in the bot sends a one-time login link (10 minutes, single use). No passwords; links and sessions are random tokens stored as SHA-256 hashes; HttpOnly, SameSite=Lax cookie, Secure on https; CSRF header on every change.
- Pages: Deadlines (recent overdue + next 14 days, tick/untick), Stats (totals and per course), Settings (reminder times, weekly plan, quiet hours, group per course). Kyiv time, light and dark theme, phone-friendly.
- REST API under `/api`, sharing the bot's rules (`DeadlineActions`, group filter).
- One Docker image serves the bot, the API and the dashboard; CI also builds the frontend.

## 1.0.0 — 2026-11-19

First version: the Telegram bot.

**Connecting**
- Paste the Moodle calendar export link; it is checked, test-read and stored encrypted (AES-GCM). The message with the link is deleted from the chat.
- `/start` with a "how to get the link" button, `/help`, and a ☰ command menu.

**Reminders**
- 3 days, 1 day and 3 hours before each deadline (toggle in `/settings`), each one exactly once per due date; a moved deadline is reminded again.
- Sunday 19:00 plan for the week.
- Quiet hours 23:00–08:00 Kyiv: night reminders arrive at 08:00, unless the deadline comes first.
- "⏰ +1 год" snooze; never later than 10 minutes before the deadline.

**Deadlines**
- `/deadlines`: the week grouped by day, recent overdue first, numbered "✅ n" buttons.
- "✅ Здано" in reminders and the list, with "↩️ Скасувати" to undo.
- `/group`: seminar group per course, so other groups' quizzes are hidden.
- `/stats`: on time, late, overdue and upcoming, with progress bars per course.

**Reliability**
- Sync every 30 minutes; a Moodle outage is not reported as a broken link; one user's failure never stops the jobs for others; a user who blocks the bot is deactivated.
- 139 tests (unit + integration on PostgreSQL via Testcontainers), CI on GitHub Actions, Docker image and production compose with daily backups.

## Next
- Deploy to a server with HTTPS and invite classmates.
