-- when the feed last failed; a dead link is retried every 6 hours instead of every 30 minutes
alter table calendar_feed add column last_failed_at timestamptz;
