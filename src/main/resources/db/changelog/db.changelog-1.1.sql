-- liquibase formatted sql

-- changeset payment:002-currency-default-usd
-- comment: Change default currency from EUR to USD
ALTER TABLE payments ALTER COLUMN currency SET DEFAULT 'USD';

-- changeset payment:003-update-existing-eur-to-usd
-- comment: Migrate existing EUR payments to USD
UPDATE payments SET currency = 'USD' WHERE currency = 'EUR';
