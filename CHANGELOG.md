# Changelog

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
- Web dashboard (phase 2): login through the bot, upcoming list, stats and settings in the browser.
- Deploy to a server and invite classmates.
