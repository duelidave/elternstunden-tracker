-- M2: family assignment and master data (FS-REG 7.1, FS-ADM 7.7, DP-05).

-- Candidate suggestions for unassigned users use the Levenshtein distance of the normalized last names (7.1.7)
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

-- Corrections of the child data on /pending, at most 3 per account (7.1.7). Counted on the account, because
-- old claims are deleted after 30 days (DP-05).
ALTER TABLE app_user ADD COLUMN claim_corrections integer NOT NULL DEFAULT 0 CHECK (claim_corrections >= 0);

-- Set by the anonymization (DP-05 a); such an account can never be enabled again
ALTER TABLE app_user ADD COLUMN anonymized_at timestamptz;

-- At most one open claim per user: neither resolved nor superseded
CREATE UNIQUE INDEX registration_claim_open_idx ON registration_claim (user_id)
    WHERE resolved_at IS NULL AND superseded_at IS NULL;

CREATE INDEX child_enrollment_year_idx ON child_enrollment (school_year_id);
