create table app_user (
    id             bigserial primary key,
    telegram_id    bigint      not null unique,
    username       text,
    first_name     text,
    active         boolean     not null default true,
    digest_enabled boolean     not null default true,
    created_at     timestamptz not null default now()
);

create table calendar_feed (
    id                   bigserial primary key,
    user_id              bigint not null unique references app_user (id) on delete cascade,
    url_encrypted        bytea  not null,
    last_synced_at       timestamptz,
    last_status          text,
    consecutive_failures int    not null default 0
);

create table deadline (
    id           bigserial primary key,
    user_id      bigint      not null references app_user (id) on delete cascade,
    external_uid text        not null,
    course_name  text,
    title        text        not null,
    due_at       timestamptz not null,
    url          text,
    status       text        not null default 'OPEN' check (status in ('OPEN', 'DONE', 'GONE')),
    done_at      timestamptz,
    unique (user_id, external_uid)
);

create index deadline_open_due_idx on deadline (due_at) where status = 'OPEN';

create table reminder_setting (
    user_id        bigint not null references app_user (id) on delete cascade,
    offset_minutes int    not null,
    primary key (user_id, offset_minutes)
);

-- due_at is part of the key so a moved deadline gets its reminders again
create table sent_reminder (
    deadline_id    bigint      not null references deadline (id) on delete cascade,
    offset_minutes int         not null,
    due_at         timestamptz not null,
    sent_at        timestamptz not null default now(),
    primary key (deadline_id, offset_minutes, due_at)
);
