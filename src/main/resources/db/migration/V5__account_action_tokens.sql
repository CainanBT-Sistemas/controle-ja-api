ALTER TABLE users
    ADD COLUMN local_password_enabled boolean NOT NULL DEFAULT true;

ALTER TABLE users
    ADD COLUMN session_version bigint NOT NULL DEFAULT 0;

UPDATE users
SET local_password_enabled = false
WHERE oauth2user = true
  AND oauth2provider = 'GOOGLE';

CREATE UNIQUE INDEX uk_users_email_case_insensitive
    ON users (lower(email));

CREATE TABLE account_action_tokens (
    id uuid NOT NULL,
    user_id uuid,
    purpose varchar(32) NOT NULL,
    email_hash varchar(64) NOT NULL,
    ip_hash varchar(64) NOT NULL,
    code_digest varchar(64) NOT NULL,
    expires_at bigint NOT NULL,
    attempts integer NOT NULL,
    max_attempts integer NOT NULL,
    consumed_at bigint,
    invalidated_at bigint,
    created_at bigint NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_action_tokens PRIMARY KEY (id),
    CONSTRAINT fk_account_action_tokens_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_account_action_tokens_purpose CHECK (purpose IN ('PASSWORD_RESET', 'ACCOUNT_REACTIVATION')),
    CONSTRAINT chk_account_action_tokens_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

CREATE INDEX idx_account_action_email_purpose_created
    ON account_action_tokens (email_hash, purpose, created_at DESC);

CREATE INDEX idx_account_action_ip_created
    ON account_action_tokens (ip_hash, created_at DESC);
