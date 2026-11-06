ALTER TABLE users
    ADD COLUMN email_verified boolean;

UPDATE users
SET email_verified = (
    oauth2user = true
    AND oauth2provider = 'GOOGLE'
    AND oauth2provider_id IS NOT NULL
    AND btrim(oauth2provider_id) <> ''
);

UPDATE users
SET refresh_token = NULL,
    refresh_token_expiry = 0,
    session_version = session_version + 1
WHERE email_verified = false;

ALTER TABLE users
    ALTER COLUMN email_verified SET DEFAULT false,
    ALTER COLUMN email_verified SET NOT NULL;

ALTER TABLE account_action_tokens
    DROP CONSTRAINT chk_account_action_tokens_purpose;

ALTER TABLE account_action_tokens
    ADD CONSTRAINT chk_account_action_tokens_purpose
        CHECK (purpose IN ('PASSWORD_RESET', 'ACCOUNT_REACTIVATION', 'EMAIL_VERIFICATION'));
