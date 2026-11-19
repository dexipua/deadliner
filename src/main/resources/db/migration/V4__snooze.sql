-- "remind me again in an hour"
create table snooze (
    deadline_id bigint      primary key references deadline (id) on delete cascade,
    remind_at   timestamptz not null
);
