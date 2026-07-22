-- Idempotent refunds: unique key + one refund per payment + request hash.
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(200);
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

UPDATE refunds
SET idempotency_key = 'legacy-refund:' || id::text
WHERE idempotency_key IS NULL;

UPDATE refunds
SET request_hash = LPAD(REPLACE(id::text, '-', ''), 64, '0')
WHERE request_hash IS NULL;

ALTER TABLE refunds ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE refunds ALTER COLUMN request_hash SET NOT NULL;

ALTER TABLE refunds DROP CONSTRAINT IF EXISTS uq_refunds_idempotency_key;
ALTER TABLE refunds ADD CONSTRAINT uq_refunds_idempotency_key UNIQUE (idempotency_key);

ALTER TABLE refunds DROP CONSTRAINT IF EXISTS uq_refunds_payment;
ALTER TABLE refunds ADD CONSTRAINT uq_refunds_payment UNIQUE (payment_id);
