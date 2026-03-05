-- src/main/resources/db/migration/V10__create_receipt_ocr_and_items.sql

CREATE TABLE IF NOT EXISTS receipt_ocr_results (
  receipt_id UUID PRIMARY KEY,
  raw_text TEXT NOT NULL,
  ocr_json JSONB NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'AZURE_VISION',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_receipt_ocr_receipt
    FOREIGN KEY (receipt_id) REFERENCES receipts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS receipt_items (
  id UUID PRIMARY KEY,
  receipt_id UUID NOT NULL,
  line_no INT NOT NULL,
  raw_line TEXT NOT NULL,
  item_name TEXT NULL,
  quantity NUMERIC(12,3) NULL,
  unit_price NUMERIC(12,2) NULL,
  amount NUMERIC(12,2) NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'SGD',
  category VARCHAR(64) NULL,
  confidence NUMERIC(5,2) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_receipt_items_receipt
    FOREIGN KEY (receipt_id) REFERENCES receipts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_receipt_items_receipt_id ON receipt_items(receipt_id);
CREATE INDEX IF NOT EXISTS idx_receipt_items_receipt_line ON receipt_items(receipt_id, line_no);