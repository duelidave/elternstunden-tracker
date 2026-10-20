-- Initial schema. Source: Lastenheft section 5.3 (v0.3); invariants in 5.4.
-- Runs as the owner role (OPS-14). Grants for the app role follow in V2.

CREATE EXTENSION IF NOT EXISTS citext;

-- ---------------------------------------------------------------
-- School year (D-01). Default 1 Sep to 31 Aug, cut-off dates adjustable.
-- ---------------------------------------------------------------
CREATE TABLE school_year (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label            text        NOT NULL UNIQUE,            -- e.g. '2026/27'
    starts_on        date        NOT NULL,
    ends_on          date        NOT NULL,
    required_minutes integer     NOT NULL DEFAULT 600 CHECK (required_minutes >= 0),  -- D-02: 10 h
    closed_at        timestamptz,                            -- OPEN-06: set = year closed, read-only
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT school_year_range_ck CHECK (ends_on > starts_on),
    CONSTRAINT school_year_no_overlap
        EXCLUDE USING gist (daterange(starts_on, ends_on, '[]') WITH &&)
);

-- ---------------------------------------------------------------
-- Family and child (D-03)
-- ---------------------------------------------------------------
CREATE TABLE family (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label      text,                                         -- free label for admins, e.g. 'Familie Müller'
    note       text,                                         -- internal admin note
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE child (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       uuid NOT NULL REFERENCES family (id),
    first_name      text NOT NULL CHECK (btrim(first_name) <> ''),
    last_name       text NOT NULL CHECK (btrim(last_name) <> ''),
    birth_date      date NOT NULL,
    first_name_norm text NOT NULL,                           -- normalization see 7.1.3, set by the service
    last_name_norm  text NOT NULL,
    external_ref    text UNIQUE,                             -- anchor for third-party systems (D-04)
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX child_match_idx  ON child (last_name_norm, first_name_norm, birth_date);
CREATE INDEX child_family_idx ON child (family_id);

-- OPEN-04: which children attend in a school year. A family owes hours
-- if at least one child is enrolled.
CREATE TABLE child_enrollment (
    child_id       uuid NOT NULL REFERENCES child (id),
    school_year_id uuid NOT NULL REFERENCES school_year (id),
    PRIMARY KEY (child_id, school_year_id)
);

-- ---------------------------------------------------------------
-- Users (D-08, D-13, D-24)
-- ---------------------------------------------------------------
CREATE TYPE user_role   AS ENUM ('FAMILY_MEMBER', 'ADMIN', 'MASTER_ADMIN');
CREATE TYPE user_status AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED');  -- PENDING = magic link not redeemed yet

CREATE TABLE app_user (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email              citext      NOT NULL UNIQUE,
    password_hash      text,                                 -- Argon2id (D-21), ONLY for MASTER_ADMIN (D-24)
    display_name       text        NOT NULL CHECK (btrim(display_name) <> ''),
    role               user_role   NOT NULL DEFAULT 'FAMILY_MEMBER',
    status             user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    family_id          uuid REFERENCES family (id),          -- NULL = not assigned
    managed_by_config  boolean     NOT NULL DEFAULT false,   -- true only for the master admin (D-22)
    email_verified_at  timestamptz,                          -- set on first magic link redemption or via OIDC
    totp_secret_enc    bytea,                                -- OPEN-09: admin TOTP only, encrypted (SEC-05); master TOTP comes from config
    last_login_at      timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_master_ck CHECK (managed_by_config = (role = 'MASTER_ADMIN')),
    -- D-24: only the master has a password, and it MUST have one (D-22: master is local only)
    CONSTRAINT app_user_password_master_only_ck CHECK ((password_hash IS NOT NULL) = (role = 'MASTER_ADMIN')),
    CONSTRAINT app_user_active_verified_ck CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL)
);
CREATE INDEX app_user_family_idx ON app_user (family_id);
-- INV-08: at most one config-managed user
CREATE UNIQUE INDEX app_user_single_master_idx ON app_user (managed_by_config) WHERE managed_by_config;

-- External OIDC identities (D-08, D-23). Key is (issuer, subject), never the email.
CREATE TABLE user_identity (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider      text        NOT NULL,                      -- registration id, e.g. 'google'
    issuer        text        NOT NULL,                      -- 'iss' claim, e.g. 'https://accounts.google.com'
    subject       text        NOT NULL,                      -- 'sub' claim, stable id at the provider
    email_at_link text,                                      -- email at link time (display only)
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    CONSTRAINT user_identity_unique UNIQUE (issuer, subject)
);
CREATE INDEX user_identity_user_idx ON user_identity (user_id);

-- Child data given at registration. Helps admins to correct assignments (D-05)
-- and provides the counters for the per-child rate limit (SEC-04).
CREATE TABLE registration_claim (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    child_first_name text NOT NULL,
    child_last_name  text NOT NULL,
    child_birth_date date NOT NULL,
    claim_key        text NOT NULL,                          -- SHA-256 hex of norm(first)|norm(last)|birth date
    match_result     text NOT NULL
        CHECK (match_result IN ('MATCHED', 'NO_MATCH', 'AMBIGUOUS', 'THROTTLED')),
    matched_child_id uuid REFERENCES child (id),
    binding_hash     text,                                   -- SHA-256 of the auth_binding cookie at creation (7.2), NULL for OIDC
    created_at       timestamptz NOT NULL DEFAULT now(),
    resolved_at      timestamptz,                            -- set on assignment (automatic or by an admin)
    superseded_at    timestamptz                             -- set when a newer claim applies or the claim was discarded
);
CREATE INDEX registration_claim_key_idx ON registration_claim (claim_key, created_at);
CREATE INDEX registration_claim_user_idx ON registration_claim (user_id);

-- Magic link (FS-AUTH 7.2, SEC-17). One mechanism for login and completing a registration.
CREATE TABLE user_token (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose      text        NOT NULL CHECK (purpose IN ('LOGIN')),  -- text instead of ENUM: new values without ALTER TYPE
    token_hash   text        NOT NULL UNIQUE,                -- SHA-256 hex of the random token, plain text is never stored
    binding_hash text        NOT NULL,                       -- SHA-256 of the auth_binding cookie at request time (7.2)
    expires_at   timestamptz NOT NULL,                       -- request time + 15 minutes
    used_at      timestamptz,                                -- set on redemption, invalid afterwards
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX user_token_user_idx ON user_token (user_id, purpose);  -- remove older tokens on a new request

-- Auth outbox (7.2, 7.11). Anonymous auth requests synchronously write only this one row
-- (same operation for every case, no oracle). A worker processes and then deletes it.
CREATE TABLE auth_request (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind            text        NOT NULL CHECK (kind IN ('REGISTER', 'MAGIC_LINK')),
    email           citext      NOT NULL,
    payload         jsonb,                                   -- REGISTER only: display name, child data
    binding_hash    text        NOT NULL,                    -- SHA-256 of the auth_binding cookie (7.2)
    attempts        integer     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    created_at      timestamptz NOT NULL                     -- set from the injected Clock (0.4 rule 7)
);
CREATE INDEX auth_request_due_idx ON auth_request (next_attempt_at);

-- ---------------------------------------------------------------
-- Parent hours (D-09, D-10)
-- ---------------------------------------------------------------
CREATE TYPE entry_status AS ENUM ('OPEN', 'CONFIRMED');

CREATE TABLE time_entry (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id        uuid         NOT NULL REFERENCES family (id),
    school_year_id   uuid         NOT NULL REFERENCES school_year (id),
    created_by       uuid         NOT NULL REFERENCES app_user (id),
    work_date        date         NOT NULL,
    start_time       time,                                   -- NULL for quick entry, set for exact entry
    duration_minutes integer      NOT NULL,
    description      text         NOT NULL,
    status           entry_status NOT NULL DEFAULT 'OPEN',
    confirmed_by     uuid REFERENCES app_user (id),
    confirmed_at     timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    version          integer      NOT NULL DEFAULT 0,        -- optimistic locking (@Version), raised on every change
    -- 15-minute grid (D-09). OPEN-10: upper bound of 720 min is a proposal.
    CONSTRAINT time_entry_duration_ck
        CHECK (duration_minutes > 0 AND duration_minutes % 15 = 0 AND duration_minutes <= 720),
    CONSTRAINT time_entry_description_ck
        CHECK (char_length(btrim(description)) BETWEEN 3 AND 500),
    CONSTRAINT time_entry_confirm_ck
        CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL))
);
CREATE INDEX time_entry_family_year_idx ON time_entry (family_id, school_year_id) WHERE deleted_at IS NULL;
CREATE INDEX time_entry_open_idx        ON time_entry (created_at)                WHERE deleted_at IS NULL AND status = 'OPEN';

-- ---------------------------------------------------------------
-- Audit log (append-only, see SEC-11; the app role only gets INSERT and SELECT, V2)
-- ---------------------------------------------------------------
CREATE TABLE audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    actor_user_id uuid,                                      -- deliberately without FK: append-only, never activated accounts get deleted
    actor_kind    text        NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM', 'INTEGRATION')),
    action        text        NOT NULL,                      -- e.g. 'ENTRY_CONFIRMED', 'USER_ASSIGNED_TO_FAMILY'
    entity_type   text        NOT NULL,
    entity_id     text,
    details       jsonb       NOT NULL DEFAULT '{}'::jsonb   -- IDs and technical values only, no free text (SEC-12)
);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, occurred_at DESC);

-- ---------------------------------------------------------------
-- Integration (FS-INT)
-- ---------------------------------------------------------------
CREATE TABLE api_client (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name         text        NOT NULL,
    purpose      text        NOT NULL,                       -- documented purpose of the integration (data protection)
    key_hash     text        NOT NULL UNIQUE,                -- SHA-256 hex of the key, plain text shown only once at creation
    scopes       text[]      NOT NULL,                       -- e.g. {'children:read','families:read'}
    created_by   uuid        NOT NULL REFERENCES app_user (id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_at   timestamptz
);
