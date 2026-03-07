ALTER TABLE receipt_items
    ADD COLUMN IF NOT EXISTS category VARCHAR(50);

ALTER TABLE receipt_items
    ADD COLUMN IF NOT EXISTS confidence NUMERIC(5,2);

UPDATE receipt_items
SET category = 'OTHER'
WHERE category IS NULL;

UPDATE receipt_items
SET confidence = 0.30
WHERE confidence IS NULL;