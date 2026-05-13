alter table app_user add column anonymous_id varchar(128);
alter table app_user add column device_secret_hash varchar(128);
alter table app_user alter column email drop not null;

create unique index idx_app_user_anonymous_id on app_user(anonymous_id);
