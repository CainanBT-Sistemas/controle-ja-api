CREATE INDEX idx_installment_plan_purge_deleted_id
    ON installment_plan (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_transactions_purge_deleted_id
    ON transactions (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_invoicess_purge_deleted_id
    ON invoicess (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_gas_stations_purge_deleted_id
    ON gas_stations (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_recurrence_rules_purge_deleted_id
    ON recurrence_rules (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_credit_cards_purge_deleted_id
    ON credit_cards (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_vehicles_purge_deleted_id
    ON vehicles (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_category_purge_deleted_id
    ON category (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_accounts_purge_deleted_id
    ON accounts (deleted_at, id)
    WHERE deleted_at IS NOT NULL;
