CREATE INDEX idx_users_deleted_id
    ON users (deleted_at, id)
    WHERE deleted_at IS NOT NULL;
