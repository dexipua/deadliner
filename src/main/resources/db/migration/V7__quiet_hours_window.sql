-- each student chooses their own quiet hours (Kyiv time, whole hours)
alter table app_user add column quiet_start int not null default 23;
alter table app_user add column quiet_end   int not null default 8;
