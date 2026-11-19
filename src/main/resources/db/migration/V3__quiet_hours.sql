-- hold night reminders (23:00-08:00 Kyiv) until morning
alter table app_user add column quiet_hours boolean not null default true;
