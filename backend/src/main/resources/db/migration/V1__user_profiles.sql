create table app_user (
    id uuid primary key,
    email varchar(320) not null unique,
    display_name varchar(160),
    created_at timestamp with time zone not null,
    last_login_at timestamp with time zone,
    last_active_at timestamp with time zone
);

create table user_preference (
    user_id uuid primary key references app_user(id) on delete cascade,
    theme varchar(32) not null,
    text_size varchar(32) not null,
    preferred_study_landing varchar(64) not null,
    updated_at timestamp with time zone not null
);

create table auth_login_token (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete cascade,
    token_hash varchar(128) not null unique,
    code_hash varchar(128) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    request_ip varchar(80),
    user_agent varchar(512)
);

create index idx_auth_login_token_token_hash on auth_login_token(token_hash);
create index idx_auth_login_token_code_user on auth_login_token(user_id, code_hash);

create table user_session (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete cascade,
    token_hash varchar(128) not null unique,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);

create index idx_user_session_token_hash on user_session(token_hash);

create table uploaded_file (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete cascade,
    stored_file_name varchar(512) not null,
    original_file_name varchar(512) not null,
    size_bytes bigint not null,
    relative_path varchar(1024) not null,
    is_pdf boolean not null,
    extracted_text_truncated boolean,
    created_at timestamp with time zone not null
);

create index idx_uploaded_file_user_created on uploaded_file(user_id, created_at desc);

create table study_session (
    id uuid primary key,
    public_id varchar(64) not null unique,
    user_id uuid not null references app_user(id) on delete cascade,
    upload_id uuid references uploaded_file(id) on delete set null,
    current_index integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    last_opened_at timestamp with time zone not null
);

create index idx_study_session_user_last_opened on study_session(user_id, last_opened_at desc);

create table study_section (
    id uuid primary key,
    session_id uuid not null references study_session(id) on delete cascade,
    section_index integer not null,
    original_content text not null,
    summary text not null,
    summarization_failure_reason varchar(64) not null,
    unique (session_id, section_index)
);

create index idx_study_section_session_index on study_section(session_id, section_index);

create table quiz_cache (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete cascade,
    session_id uuid not null references study_session(id) on delete cascade,
    section_index integer not null,
    questions_json text not null,
    last_correct_count integer,
    last_total_count integer,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (session_id, section_index)
);

create index idx_quiz_cache_user_session on quiz_cache(user_id, session_id);
