CREATE TABLE users (
    account_non_expired boolean NOT NULL,
    account_non_locked boolean NOT NULL,
    credentials_non_expired boolean NOT NULL,
    enabled boolean NOT NULL,
    oauth2user boolean NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    refresh_token_expiry bigint,
    updated_at bigint,
    id uuid NOT NULL,
    email varchar(255) NOT NULL,
    last_ip varchar(255),
    last_user_agent varchar(255),
    oauth2provider text,
    oauth2provider_id text,
    password varchar(255) NOT NULL,
    refresh_token text,
    role varchar(255) NOT NULL,
    username varchar(255) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE accounts (
    calculate_balance boolean NOT NULL,
    current_balance numeric(38,2) NOT NULL,
    enabled boolean NOT NULL,
    initial_balance numeric(38,2) NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    color varchar(255),
    currency varchar(255) NOT NULL,
    icon varchar(255),
    institution varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT chk_accounts_type CHECK (
        type IN ('WALLET', 'BANK', 'SAVINGS', 'INVESTMENT', 'CREDIT_CARD')
    )
);

CREATE TABLE category (
    enabled boolean NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    is_sub_category boolean NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    id uuid NOT NULL,
    sub_category_id uuid,
    user_id uuid NOT NULL,
    category_type varchar(255) NOT NULL,
    color varchar(255),
    icon varchar(255),
    name varchar(255) NOT NULL,
    CONSTRAINT pk_category PRIMARY KEY (id)
);

CREATE TABLE credit_cards (
    best_day integer NOT NULL,
    close_day integer NOT NULL,
    current_limit numeric(38,2) NOT NULL,
    enabled boolean NOT NULL,
    total_limit numeric(38,2) NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    account_id uuid NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    color varchar(255),
    description varchar(255),
    icon varchar(255),
    name varchar(255) NOT NULL,
    CONSTRAINT pk_credit_cards PRIMARY KEY (id),
    CONSTRAINT uk_credit_cards_account UNIQUE (account_id)
);

CREATE TABLE vehicles (
    avg_km_per_liter_ethanol double precision,
    avg_km_per_liter_gasoline double precision,
    current_odometer numeric(38,2) NOT NULL,
    initial_odometer numeric(38,2) NOT NULL,
    tank_capacity double precision,
    year integer NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    brand varchar(255) NOT NULL,
    model varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    plate varchar(255),
    CONSTRAINT pk_vehicles PRIMARY KEY (id)
);

CREATE TABLE gas_stations (
    created_at bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    address varchar(255),
    city varchar(255),
    name varchar(255) NOT NULL,
    state varchar(255),
    CONSTRAINT pk_gas_stations PRIMARY KEY (id)
);

CREATE TABLE gas_station_rankings (
    adjusted_avg_kml double precision,
    avg_cost_per_km numeric(38,2),
    avg_kml double precision,
    city_refuel_count integer,
    last_price_per_liter numeric(38,2),
    refuel_count integer,
    road_refuel_count integer,
    score double precision,
    total_adjusted_distance double precision,
    total_amount numeric(38,2),
    total_distance double precision,
    total_liters double precision,
    unknown_refuel_count integer,
    updated_at bigint,
    gas_station_id uuid,
    id uuid NOT NULL,
    fuel_type varchar(255),
    CONSTRAINT pk_gas_station_rankings PRIMARY KEY (id),
    CONSTRAINT chk_gas_station_rankings_fuel_type CHECK (
        fuel_type IN ('GASOLINA', 'ETANOL', 'DIESEL', 'GNV', 'ELETRICO', 'OUTRO')
    )
);

CREATE TABLE recurrence_rules (
    base_amount numeric(38,2) NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    end_date bigint,
    start_date bigint NOT NULL,
    updated_at bigint,
    account_id uuid NOT NULL,
    category_id uuid NOT NULL,
    id uuid NOT NULL,
    target_account_id uuid,
    user_id uuid NOT NULL,
    description varchar(255),
    frequency varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    CONSTRAINT pk_recurrence_rules PRIMARY KEY (id),
    CONSTRAINT chk_recurrence_rules_frequency CHECK (
        frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'YEARLY')
    ),
    CONSTRAINT chk_recurrence_rules_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'CANCELED', 'FINISHED')
    ),
    CONSTRAINT chk_recurrence_rules_type CHECK (
        type IN (
            'RECEITA',
            'DESPESA',
            'TRANSFERENCIA',
            'TRANSFERENCIA_ENTRADA',
            'TRANSFERENCIA_SAIDA',
            'PAGAMENTO_FATURA',
            'REAJUSTE_SALDO'
        )
    )
);

CREATE TABLE transactions (
    amount numeric(38,2) NOT NULL,
    current_odometer numeric(38,2),
    efficiency double precision,
    enabled boolean NOT NULL,
    fixed boolean NOT NULL,
    liters double precision,
    paid boolean NOT NULL,
    created_at bigint NOT NULL,
    date bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    account_id uuid NOT NULL,
    category_id uuid NOT NULL,
    credit_card_id uuid,
    gas_station_id uuid,
    id uuid NOT NULL,
    parent_transaction_id uuid,
    recurrence_rule_id uuid,
    target_invoice_id uuid,
    user_id uuid NOT NULL,
    vehicle_id uuid,
    description varchar(255),
    driving_predominance varchar(255),
    fuel_type varchar(255),
    name varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT chk_transactions_driving_predominance CHECK (
        driving_predominance IN ('CITY', 'ROAD')
    ),
    CONSTRAINT chk_transactions_fuel_type CHECK (
        fuel_type IN ('GASOLINA', 'ETANOL', 'DIESEL', 'GNV', 'ELETRICO', 'OUTRO')
    ),
    CONSTRAINT chk_transactions_type CHECK (
        type IN (
            'RECEITA',
            'DESPESA',
            'TRANSFERENCIA',
            'TRANSFERENCIA_ENTRADA',
            'TRANSFERENCIA_SAIDA',
            'PAGAMENTO_FATURA',
            'REAJUSTE_SALDO'
        )
    )
);

CREATE TABLE invoicess (
    amount numeric(38,2) NOT NULL,
    enabled boolean NOT NULL,
    month integer NOT NULL,
    paid boolean NOT NULL,
    year integer NOT NULL,
    created_at bigint NOT NULL,
    deleted_at bigint,
    expiration_date bigint NOT NULL,
    updated_at bigint,
    credit_card_id uuid NOT NULL,
    id uuid NOT NULL,
    transaction_id uuid,
    user_id uuid NOT NULL,
    CONSTRAINT pk_invoicess PRIMARY KEY (id)
);

CREATE TABLE installment_plan (
    amount numeric(38,2) NOT NULL,
    current_installment integer NOT NULL,
    enabled boolean NOT NULL,
    fixed boolean NOT NULL,
    paid boolean NOT NULL,
    total_installments_plan integer NOT NULL,
    created_at bigint NOT NULL,
    date bigint NOT NULL,
    deleted_at bigint,
    updated_at bigint,
    id uuid NOT NULL,
    invoices_id uuid NOT NULL,
    purchase_id uuid NOT NULL,
    user_id uuid NOT NULL,
    description varchar(255),
    name varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    CONSTRAINT pk_installment_plan PRIMARY KEY (id)
);

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE category
    ADD CONSTRAINT fk_category_parent
    FOREIGN KEY (sub_category_id) REFERENCES category (id);

ALTER TABLE category
    ADD CONSTRAINT fk_category_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE credit_cards
    ADD CONSTRAINT fk_credit_cards_account
    FOREIGN KEY (account_id) REFERENCES accounts (id);

ALTER TABLE credit_cards
    ADD CONSTRAINT fk_credit_cards_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicles_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE gas_stations
    ADD CONSTRAINT fk_gas_stations_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE gas_station_rankings
    ADD CONSTRAINT fk_gas_station_rankings_station
    FOREIGN KEY (gas_station_id) REFERENCES gas_stations (id);

ALTER TABLE recurrence_rules
    ADD CONSTRAINT fk_recurrence_rules_account
    FOREIGN KEY (account_id) REFERENCES accounts (id);

ALTER TABLE recurrence_rules
    ADD CONSTRAINT fk_recurrence_rules_category
    FOREIGN KEY (category_id) REFERENCES category (id);

ALTER TABLE recurrence_rules
    ADD CONSTRAINT fk_recurrence_rules_target_account
    FOREIGN KEY (target_account_id) REFERENCES accounts (id);

ALTER TABLE recurrence_rules
    ADD CONSTRAINT fk_recurrence_rules_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_account
    FOREIGN KEY (account_id) REFERENCES accounts (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_category
    FOREIGN KEY (category_id) REFERENCES category (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_credit_card
    FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_gas_station
    FOREIGN KEY (gas_station_id) REFERENCES gas_stations (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_parent
    FOREIGN KEY (parent_transaction_id) REFERENCES transactions (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_recurrence_rule
    FOREIGN KEY (recurrence_rule_id) REFERENCES recurrence_rules (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_vehicle
    FOREIGN KEY (vehicle_id) REFERENCES vehicles (id);

ALTER TABLE invoicess
    ADD CONSTRAINT fk_invoicess_credit_card
    FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id);

ALTER TABLE invoicess
    ADD CONSTRAINT fk_invoicess_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions (id);

ALTER TABLE invoicess
    ADD CONSTRAINT fk_invoicess_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_target_invoice
    FOREIGN KEY (target_invoice_id) REFERENCES invoicess (id);

ALTER TABLE installment_plan
    ADD CONSTRAINT fk_installment_plan_invoice
    FOREIGN KEY (invoices_id) REFERENCES invoicess (id);

ALTER TABLE installment_plan
    ADD CONSTRAINT fk_installment_plan_user
    FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_users_email_deleted
    ON users (email, deleted_at);
CREATE INDEX idx_users_enabled_locked_deleted
    ON users (enabled, account_non_locked, deleted_at);

CREATE INDEX idx_accounts_user_deleted_type
    ON accounts (user_id, deleted_at, type);
CREATE INDEX idx_accounts_user_name_type_deleted
    ON accounts (user_id, name, type, deleted_at);

CREATE INDEX idx_category_user_deleted_type
    ON category (user_id, deleted_at, category_type);
CREATE INDEX idx_category_parent_deleted
    ON category (sub_category_id, deleted_at);
CREATE INDEX idx_category_user_name_deleted
    ON category (user_id, name, deleted_at);

CREATE INDEX idx_credit_cards_user_deleted
    ON credit_cards (user_id, deleted_at);
CREATE INDEX idx_credit_cards_account_deleted
    ON credit_cards (account_id, deleted_at);

CREATE INDEX idx_vehicles_user_deleted
    ON vehicles (user_id, deleted_at);
CREATE INDEX idx_vehicles_user_plate_deleted
    ON vehicles (user_id, plate, deleted_at);

CREATE INDEX idx_gas_stations_user_deleted
    ON gas_stations (user_id, deleted_at);
CREATE INDEX idx_gas_stations_user_name_deleted
    ON gas_stations (user_id, name, deleted_at);

CREATE INDEX idx_gas_rankings_station_fuel
    ON gas_station_rankings (gas_station_id, fuel_type);
CREATE INDEX idx_gas_rankings_score
    ON gas_station_rankings (score);

CREATE INDEX idx_recurrence_user_status_deleted
    ON recurrence_rules (user_id, status, deleted_at);
CREATE INDEX idx_recurrence_status_deleted
    ON recurrence_rules (status, deleted_at);
CREATE INDEX idx_recurrence_account_deleted
    ON recurrence_rules (account_id, deleted_at);

CREATE INDEX idx_transactions_user_deleted_date
    ON transactions (user_id, deleted_at, date);
CREATE INDEX idx_transactions_user_type_paid_date
    ON transactions (user_id, type, paid, date);
CREATE INDEX idx_transactions_recurrence_paid_date
    ON transactions (recurrence_rule_id, paid, date);
CREATE INDEX idx_transactions_parent_deleted
    ON transactions (parent_transaction_id, deleted_at);
CREATE INDEX idx_transactions_vehicle_date
    ON transactions (vehicle_id, date);
CREATE INDEX idx_transactions_credit_card_date
    ON transactions (credit_card_id, date);
CREATE INDEX idx_transactions_target_invoice
    ON transactions (target_invoice_id);

CREATE INDEX idx_invoices_user_expiration_deleted
    ON invoicess (user_id, expiration_date, deleted_at);
CREATE INDEX idx_invoices_user_card_expiration
    ON invoicess (user_id, credit_card_id, expiration_date);
CREATE INDEX idx_invoices_card_month_year
    ON invoicess (credit_card_id, month, year);
CREATE INDEX idx_invoices_user_paid_amount_expiration
    ON invoicess (user_id, paid, amount, expiration_date);

CREATE INDEX idx_installments_invoice_user_deleted_date
    ON installment_plan (invoices_id, user_id, deleted_at, date);
CREATE INDEX idx_installments_purchase_user_deleted
    ON installment_plan (purchase_id, user_id, deleted_at);
CREATE INDEX idx_installments_user_date_deleted
    ON installment_plan (user_id, date, deleted_at);
CREATE INDEX idx_installments_invoice_paid_amount
    ON installment_plan (invoices_id, paid, amount);
