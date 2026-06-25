CREATE TABLE closed_test_testers (
    enabled boolean DEFAULT true NOT NULL,
    created_at bigint NOT NULL,
    disabled_at bigint,
    updated_at bigint,
    id uuid NOT NULL,
    email varchar(255) NOT NULL,
    normalized_email varchar(255) NOT NULL,
    reason varchar(255),
    CONSTRAINT pk_closed_test_testers PRIMARY KEY (id),
    CONSTRAINT uk_closed_test_testers_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_closed_test_testers_normalized_email CHECK (
        normalized_email = lower(trim(normalized_email))
        AND normalized_email <> ''
    )
);

CREATE INDEX idx_closed_test_testers_normalized_enabled
    ON closed_test_testers (normalized_email, enabled);
