-- KSE seminar groups differ per course (e.g. group 1 in MATH101, group 4 in ENG121)
create table course_group (
    user_id      bigint not null references app_user (id) on delete cascade,
    course_name  text   not null,
    group_number int    not null,
    primary key (user_id, course_name)
);

alter table app_user drop column seminar_group;
