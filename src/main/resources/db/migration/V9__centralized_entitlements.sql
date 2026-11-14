ALTER TABLE users
    ADD COLUMN plan varchar(32) NOT NULL DEFAULT 'FREE',
    ADD COLUMN entitlement_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN entitlement_touched_at bigint NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT chk_users_plan
        CHECK (plan IN ('FREE', 'FREE_NO_ADS', 'PLUS'));

ALTER TABLE closed_test_testers
    ADD COLUMN profile varchar(32) NOT NULL DEFAULT 'TESTER_PLUS';

ALTER TABLE closed_test_testers
    ADD CONSTRAINT chk_closed_test_testers_profile
        CHECK (profile IN ('TESTER_FREE', 'TESTER_PLUS'));

ALTER TABLE accounts
    ADD COLUMN plan_restricted boolean NOT NULL DEFAULT false;

ALTER TABLE credit_cards
    ADD COLUMN plan_restricted boolean NOT NULL DEFAULT false;

ALTER TABLE category
    ADD COLUMN plan_restricted boolean NOT NULL DEFAULT false;

ALTER TABLE vehicles
    ADD COLUMN plan_restricted boolean NOT NULL DEFAULT false;

CREATE INDEX idx_accounts_plan_limits
    ON accounts (user_id, deleted_at, type, plan_restricted, created_at, id);

CREATE INDEX idx_credit_cards_plan_limits
    ON credit_cards (user_id, deleted_at, plan_restricted, created_at, id);

CREATE INDEX idx_category_plan_limits
    ON category (user_id, deleted_at, is_default, is_sub_category, plan_restricted, created_at, id);

CREATE INDEX idx_vehicles_plan_limits
    ON vehicles (user_id, deleted_at, plan_restricted, created_at, id);
