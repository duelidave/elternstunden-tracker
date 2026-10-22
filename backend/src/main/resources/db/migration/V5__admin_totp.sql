-- M3: second factor for admins (SEC-05, 7.2). The secret itself lives in app_user.totp_secret_enc (V1),
-- encrypted with APP_ENCRYPTION_KEY; it counts only once confirmed.
ALTER TABLE app_user ADD COLUMN totp_enabled_at timestamptz;

-- 10 one-time recovery codes per admin, stored as SHA-256 hashes only
CREATE TABLE user_recovery_code (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash  text        NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL
);
CREATE INDEX user_recovery_code_user_idx ON user_recovery_code (user_id);
