ALTER TABLE receipt_items
ADD COLUMN subcategory VARCHAR(64);

ALTER TABLE receipt_items
ADD COLUMN category_source VARCHAR(32);

UPDATE receipt_items
SET category_source = 'RULE'
WHERE category_source IS NULL;