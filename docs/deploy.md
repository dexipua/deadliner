# Deploying Deadliner

Target: one small Linux VPS (1 vCPU, 1–2 GB RAM) with Docker. Phase 1 needs no domain and no open ports: the bot uses long polling.

## 1. Server

```bash
# on a fresh Ubuntu VPS
curl -fsSL https://get.docker.com | sh
git clone https://github.com/dexipua/deadliner.git && cd deadliner
```

## 2. Secrets

```bash
cp .env.example .env
nano .env
```

| Variable | Value |
|---|---|
| `TELEGRAM_BOT_TOKEN` | from @BotFather |
| `DEADLINER_FEED_KEY` | `openssl rand -base64 32`. **Back it up.** Without it, the stored links cannot be decrypted. |
| `DB_USER`, `DB_PASSWORD` | anything strong; Postgres is created with them |
| `DB_URL` | leave as is; compose overrides it |

`chmod 600 .env`.

## 3. Start

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml logs -f app
```

Check: send `/start` to the bot.

## 3a. HTTPS for the web dashboard

The app listens on `127.0.0.1:8080` only. Put a reverse proxy with HTTPS in front, for example Caddy, which gets the certificate by itself:

```
# /etc/caddy/Caddyfile
deadliner.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Then set `DEADLINER_WEB_URL=https://deadliner.example.com` in `.env` and restart the app: `/web` links point there, and the session cookie becomes `Secure`.

Notes: if `DEADLINER_WEB_URL` is left at the local default, `/web` links point to the server itself and the app logs a warning at start. The proxy's access log will contain `/auth?t=…`; those links are single-use and expire in 10 minutes, so that is harmless, but don't share the logs.

## 4. Backups

The `backup` service writes `backups/deadliner-YYYY-MM-DD.dump` once a day and keeps 14 days. Copy them off the server now and then.

Restore:

```bash
docker compose -f compose.prod.yaml exec -T postgres pg_restore -U "$DB_USER" -d deadliner --clean < backups/deadliner-2026-12-01.dump
```

## 5. Update

```bash
git pull && docker compose -f compose.prod.yaml up -d --build
```

Flyway applies new migrations on start.
