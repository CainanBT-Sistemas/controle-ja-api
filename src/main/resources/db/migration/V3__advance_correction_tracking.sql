ALTER TABLE installment_plan
    ADD COLUMN advance_operation_id uuid,
    ADD COLUMN advanced_from_invoice_id uuid,
    ADD COLUMN advance_corrected_at bigint;

ALTER TABLE installment_plan
    ADD CONSTRAINT fk_installment_advanced_from_invoice
        FOREIGN KEY (advanced_from_invoice_id) REFERENCES invoicess(id);

CREATE INDEX idx_installments_advance_operation
    ON installment_plan (advance_operation_id, user_id, deleted_at);
