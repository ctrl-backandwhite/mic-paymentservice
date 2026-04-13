-- liquibase formatted sql

-- changeset payment:004-add-settlement-columns
ALTER TABLE payments ADD COLUMN IF NOT EXISTS settlement_amount  NUMERIC(15,2);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(10);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS exchange_rate       NUMERIC(18,8);
