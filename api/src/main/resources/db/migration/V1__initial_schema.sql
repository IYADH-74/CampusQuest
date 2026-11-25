-- CampusQuest v3 initial schema.
-- Invariants live here as well as in code: the database is the last line of defence.

create table cohorts (
    id          uuid         primary key,
    code        varchar(32)  not null,
    name        varchar(120) not null,
    archived    boolean      not null default false,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    version     bigint       not null,
    constraint uq_cohorts_code unique (code),
    constraint ck_cohorts_code check (code ~ '^[A-Z0-9][A-Z0-9_-]{0,31}$')
);

create table users (
    id             uuid         primary key,
    email          varchar(254) not null,
    password_hash  varchar(255),
    first_name     varchar(80)  not null,
    last_name      varchar(80)  not null,
    role           varchar(16)  not null,
    status         varchar(32)  not null,
    token_version  integer      not null default 0,
    department     varchar(120),
    subject        varchar(120),
    cohort_id      uuid         references cohorts (id),
    xp             bigint       not null default 0,
    level          integer      not null default 0,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    version        bigint       not null,
    constraint uq_users_email unique (email),
    constraint ck_users_email_lower check (email = lower(email)),
    constraint ck_users_role check (role in ('ADMIN', 'STAFF', 'TEACHER', 'STUDENT')),
    constraint ck_users_status check (status in ('PENDING_VERIFICATION', 'INVITED', 'ACTIVE', 'DISABLED')),
    constraint ck_users_xp check (xp >= 0),
    constraint ck_users_level check (level between 0 and 100),
    -- role-specific attributes may only be set on the matching role
    constraint ck_users_cohort_student check (cohort_id is null or role = 'STUDENT'),
    constraint ck_users_department_staff check (department is null or role = 'STAFF'),
    constraint ck_users_subject_teacher check (subject is null or role = 'TEACHER'),
    constraint ck_users_xp_student check (role = 'STUDENT' or (xp = 0 and level = 0)),
    -- an active account always has a password
    constraint ck_users_active_has_password check (status not in ('ACTIVE', 'PENDING_VERIFICATION') or password_hash is not null)
);
create index ix_users_role_status on users (role, status);
create index ix_users_cohort on users (cohort_id) where cohort_id is not null;
create index ix_users_leaderboard on users (xp desc, id) where role = 'STUDENT' and status = 'ACTIVE';

create table teaching_assignments (
    cohort_id   uuid not null references cohorts (id),
    teacher_id  uuid not null references users (id),
    primary key (cohort_id, teacher_id)
);
create index ix_teaching_assignments_teacher on teaching_assignments (teacher_id);

create table quests (
    id                uuid          primary key,
    kind              varchar(16)   not null,
    category          varchar(24)   not null,
    title             varchar(120)  not null,
    description       varchar(5000) not null,
    tier              integer       not null,
    status            varchar(16)   not null,
    author_id         uuid          not null references users (id),
    due_at            timestamptz,
    max_participants  integer,
    min_level         integer       not null default 0,
    published_at      timestamptz,
    closed_at         timestamptz,
    cancelled_at      timestamptz,
    created_at        timestamptz   not null,
    updated_at        timestamptz   not null,
    version           bigint        not null,
    constraint ck_quests_kind check (kind in ('ASSIGNMENT', 'CHALLENGE')),
    constraint ck_quests_category check (
        (kind = 'ASSIGNMENT' and category = 'COURSEWORK') or
        (kind = 'CHALLENGE' and category in ('CERTIFICATION', 'COMPETITION', 'COURSE', 'EVENT'))),
    constraint ck_quests_tier check (tier between 1 and 5),
    constraint ck_quests_status check (status in ('DRAFT', 'PUBLISHED', 'CLOSED', 'CANCELLED')),
    constraint ck_quests_assignment_due check (kind <> 'ASSIGNMENT' or due_at is not null),
    constraint ck_quests_assignment_open check (kind <> 'ASSIGNMENT' or (max_participants is null and min_level = 0)),
    constraint ck_quests_assignment_not_closed check (kind <> 'ASSIGNMENT' or status <> 'CLOSED'),
    constraint ck_quests_capacity check (max_participants is null or max_participants between 1 and 100000),
    constraint ck_quests_min_level check (min_level between 0 and 100)
);
create index ix_quests_author on quests (author_id, status);
create index ix_quests_kind_status on quests (kind, status, due_at);

create table quest_cohorts (
    quest_id   uuid not null references quests (id) on delete cascade,
    cohort_id  uuid not null references cohorts (id),
    primary key (quest_id, cohort_id)
);
create index ix_quest_cohorts_cohort on quest_cohorts (cohort_id);

create table quest_participations (
    id               uuid          primary key,
    quest_id         uuid          not null references quests (id),
    student_id       uuid          not null references users (id),
    status           varchar(16)   not null,
    joined_at        timestamptz   not null,
    submitted_at     timestamptz,
    submission_text  varchar(5000),
    submission_url   varchar(2048),
    reviewer_id      uuid          references users (id),
    reviewed_at      timestamptz,
    feedback         varchar(2000),
    xp_awarded       integer,
    revision_count   integer       not null default 0,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    version          bigint        not null,
    constraint uq_participation unique (quest_id, student_id),
    constraint ck_participation_status check (status in
        ('ACTIVE', 'SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED', 'CANCELLED')),
    constraint ck_participation_submitted check (status not in ('SUBMITTED', 'APPROVED') or submitted_at is not null),
    constraint ck_participation_url check (submission_url is null or submission_url like 'https://%')
);
create index ix_participations_student on quest_participations (student_id, status);
create index ix_participations_quest on quest_participations (quest_id, status);
create index ix_participations_review_queue on quest_participations (status, submitted_at) where status = 'SUBMITTED';

create table xp_boosts (
    id            uuid          primary key,
    multiplier    numeric(3, 2) not null,
    starts_at     timestamptz   not null,
    ends_at       timestamptz   not null,
    reason        varchar(200)  not null,
    created_by    uuid          not null references users (id),
    cancelled_at  timestamptz,
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    version       bigint        not null,
    constraint ck_boost_multiplier check (multiplier > 1.00 and multiplier <= 3.00),
    constraint ck_boost_window check (ends_at > starts_at),
    -- at most one live boost at any instant
    constraint ex_boost_overlap exclude using gist (tstzrange(starts_at, ends_at, '[)') with &&)
        where (cancelled_at is null)
);

create table xp_transactions (
    id                uuid          primary key,
    student_id        uuid          not null references users (id),
    amount            integer       not null,
    reason            varchar(24)   not null,
    participation_id  uuid          references quest_participations (id),
    boost_id          uuid          references xp_boosts (id),
    actor_id          uuid          references users (id),
    note              varchar(500),
    balance_after     bigint        not null,
    created_at        timestamptz   not null,
    constraint ck_xp_reason check (reason in ('QUEST_REWARD', 'QUEST_PENALTY', 'MANUAL_ADJUSTMENT')),
    constraint ck_xp_balance check (balance_after >= 0),
    constraint ck_xp_manual_note check (reason <> 'MANUAL_ADJUSTMENT' or (note is not null and actor_id is not null)),
    constraint ck_xp_quest_source check (reason = 'MANUAL_ADJUSTMENT' or participation_id is not null),
    -- a participation is rewarded / penalised at most once
    constraint uq_xp_participation_reason unique (participation_id, reason)
);
create index ix_xp_transactions_student on xp_transactions (student_id, created_at desc);

create table one_time_tokens (
    id           uuid         primary key,
    user_id      uuid         not null references users (id),
    purpose      varchar(24)  not null,
    token_hash   varchar(128) not null,
    expires_at   timestamptz  not null,
    consumed_at  timestamptz,
    attempts     integer      not null default 0,
    created_at   timestamptz  not null,
    constraint uq_one_time_token_hash unique (token_hash),
    constraint ck_one_time_token_purpose check (purpose in ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ACCOUNT_INVITE'))
);
create index ix_one_time_tokens_user on one_time_tokens (user_id, purpose) where consumed_at is null;

create table refresh_tokens (
    id                 uuid         primary key,
    user_id            uuid         not null references users (id),
    family_id          uuid         not null,
    token_hash         varchar(128) not null,
    expires_at         timestamptz  not null,
    family_expires_at  timestamptz  not null,
    revoked_at         timestamptz,
    replaced_by        uuid,
    user_agent         varchar(255),
    ip_address         varchar(64),
    created_at         timestamptz  not null,
    constraint uq_refresh_token_hash unique (token_hash)
);
create index ix_refresh_tokens_user on refresh_tokens (user_id) where revoked_at is null;
create index ix_refresh_tokens_family on refresh_tokens (family_id);

create table audit_events (
    id           uuid         primary key,
    occurred_at  timestamptz  not null,
    actor_id     uuid,
    action       varchar(48)  not null,
    target_type  varchar(32),
    target_id    uuid,
    ip_address   varchar(64),
    request_id   varchar(64),
    details      jsonb        not null default '{}'::jsonb
);
create index ix_audit_events_time on audit_events (occurred_at desc);
create index ix_audit_events_actor on audit_events (actor_id, occurred_at desc);
create index ix_audit_events_action on audit_events (action, occurred_at desc);
