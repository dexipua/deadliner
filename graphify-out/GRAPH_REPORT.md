# Graph Report - deadliner  (2026-11-19)

## Corpus Check
- 59 files · ~13,572 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 9 file(s) not represented in the graph (top: (none) 4, .properties 2, .example 1)

## Summary
- 579 nodes · 1433 edges · 22 communities (13 shown, 9 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 181 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `71967ee4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- FeedSyncService.java
- FeedSyncServiceIT
- .connect
- org.junit.jupiter.api.Test
- Deadline
- DeadlinerBot.java
- backup
- ReminderSettings
- IcalParser.java
- AppUser
- 5. Design
- FeedUrlCipher
- spring
- Deadliner
- steps
- .normalize
- postgres
- DeadlinerApplication.java
- Texts
- InvalidFeedException
- ua.dexipua:deadliner

## God Nodes (most connected - your core abstractions)
1. `Deadline` - 56 edges
2. `AppUser` - 34 edges
3. `FeedSyncServiceIT` - 30 edges
4. `AppUserRepository` - 28 edges
5. `DeadlineRepository` - 27 edges
6. `CommandHandlerIT` - 26 edges
7. `FeedSyncService` - 26 edges
8. `CommandHandler` - 21 edges
9. `MessageFormatter` - 19 edges
10. `ReminderSettings` - 18 edges

## Surprising Connections (you probably didn't know these)
- `Task 6: Reminder planning and sending` --references--> `Notifier`  [INFERRED]
  docs/superpowers/plans/2026-11-19-deadliner-phase1-bot.md → src/main/java/ua/dexipua/deadliner/bot/Notifier.java
- `Task 5: Feed connect and sync` --references--> `Notifier`  [INFERRED]
  docs/superpowers/plans/2026-11-19-deadliner-phase1-bot.md → src/main/java/ua/dexipua/deadliner/bot/Notifier.java
- `Task 5: Feed connect and sync` --references--> `IcalParser`  [INFERRED]
  docs/superpowers/plans/2026-11-19-deadliner-phase1-bot.md → src/main/java/ua/dexipua/deadliner/feed/IcalParser.java
- `Task 8: Telegram bot commands` --references--> `DeadlineRepository`  [INFERRED]
  docs/superpowers/plans/2026-11-19-deadliner-phase1-bot.md → src/main/java/ua/dexipua/deadliner/deadline/DeadlineRepository.java
- `Task 8: Telegram bot commands` --references--> `MessageFormatter`  [INFERRED]
  docs/superpowers/plans/2026-11-19-deadliner-phase1-bot.md → src/main/java/ua/dexipua/deadliner/reminder/MessageFormatter.java

## Import Cycles
- None detected.

## Communities (22 total, 9 thin omitted)

### Community 1 - "FeedSyncServiceIT"
Cohesion: 0.11
Nodes (19): Task 1: Project skeleton, schema, CI, Notifier, DeadlineRepository, CalendarFeedRepository, FeedFetcher, FeedSyncJob, FeedSyncService, DigestJob (+11 more)

### Community 2 - ".connect"
Cohesion: 0.06
Nodes (19): Deadliner Phase 1 (Telegram bot) Implementation Plan, Global Constraints, Review Focus, Task 0: Verify Moodle export and set up accounts (Artem, manual, ~1 h), Task 2: Feed URL encryption, Task 3: Feed URL validation, Task 4: iCal parsing and deadline filter, Task 5: Feed connect and sync (+11 more)

### Community 3 - "org.junit.jupiter.api.Test"
Cohesion: 0.10
Nodes (4): BotReply, CommandHandler, CommandHandlerIT, DigestScheduleTest

### Community 4 - "Deadline"
Cohesion: 0.09
Nodes (9): Deadline, DeadlineStatus, DONE, GONE, OPEN, Ok, ParsedDeadline, MessageFormatter (+1 more)

### Community 5 - "DeadlinerBot.java"
Cohesion: 0.08
Nodes (5): DeadlinerBot, TelegramConfig, TelegramNotifier, AppConfig, Scheduling

### Community 6 - "backup"
Cohesion: 0.06
Nodes (34): build, depends_on, env_file, environment, restart, depends_on, entrypoint, environment (+26 more)

### Community 7 - "ReminderSettings"
Cohesion: 0.13
Nodes (6): DueReminder, ReminderPlanner, SentKey, ReminderSettings, SentReminders, ReminderPlannerTest

### Community 9 - "AppUser"
Cohesion: 0.11
Nodes (3): InlineButton, AppUser, Sent

### Community 10 - "5. Design"
Cohesion: 0.10
Nodes (20): 1. Intent and why, 2. Goals, non-goals, anti-goals, 3. Constraints, 4. Approaches considered, 5.1 Architecture, 5.2 Data model (first cut), 5.3 Bot (Phase 1), 5.4 Sync (+12 more)

### Community 12 - "spring"
Cohesion: 0.11
Nodes (17): name, password, url, username, deadliner, feed-key, telegram, ddl-auto (+9 more)

### Community 13 - "Deadliner"
Cohesion: 0.12
Nodes (14): 1. Server, 2. Secrets, 3. Start, 4. Backups, 5. Update, Deploying Deadliner, Deadliner, Deploy (+6 more)

### Community 14 - "steps"
Cohesion: 0.13
Nodes (14): runs-on, steps, jobs, build, name, on, pull_request, push (+6 more)

### Community 16 - "postgres"
Cohesion: 0.17
Nodes (11): POSTGRES_DB, POSTGRES_PASSWORD, POSTGRES_USER, environment, image, ports, volumes, services (+3 more)

## Knowledge Gaps
- **4 isolated node(s):** `ua.dexipua:deadliner`, `DONE`, `GONE`, `OPEN`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 156 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Deadline` connect `Deadline` to `FeedSyncService.java`, `FeedSyncServiceIT`, `.connect`, `org.junit.jupiter.api.Test`, `ReminderSettings`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **What connects `ua.dexipua:deadliner`, `DONE`, `GONE` to the rest of the system?**
  _4 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `FeedSyncService.java` be split into smaller, more focused modules?**
  _Cohesion score 0.053946053946053944 - nodes in this community are weakly interconnected._
- **Why does `FeedSyncService` connect `FeedSyncServiceIT` to `FeedSyncService.java`, `.connect`, `org.junit.jupiter.api.Test`, `Deadline`, `ReminderSettings`, `IcalParser.java`, `FeedUrlCipher`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Should `FeedSyncServiceIT` be split into smaller, more focused modules?**
  _Cohesion score 0.11340852130325814 - nodes in this community are weakly interconnected._
- **Why does `AppUser` connect `AppUser` to `FeedSyncService.java`, `FeedSyncServiceIT`, `.connect`, `org.junit.jupiter.api.Test`, `Deadline`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **Should `.connect` be split into smaller, more focused modules?**
  _Cohesion score 0.055904961565338925 - nodes in this community are weakly interconnected._