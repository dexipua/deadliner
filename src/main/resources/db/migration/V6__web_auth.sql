-- one-time login links sent by /web; only SHA-256 hashes are stored
create table web_login_token (
    token_hash text        primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    expires_at timestamptz not null,
    used_at    timestamptz
);

-- browser sessions (cookie dl_session); only SHA-256 hashes are stored
create table web_session (
    token_hash text        primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
