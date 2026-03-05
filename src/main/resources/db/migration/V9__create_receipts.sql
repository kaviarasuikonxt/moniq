CREATE TABLE IF NOT EXISTS receipts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    merchant VARCHAR(255),
    receipt_date TIMESTAMPTZ NULL,
    total_amount NUMERIC(12,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'SGD',
    status VARCHAR(32) NOT NULL,
    blob_name TEXT NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_name TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_receipts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_receipts_user_created
    ON receipts (user_id, created_at DESC);