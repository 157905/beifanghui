ALTER TABLE bf_verification
    ADD COLUMN ticket_no INT NOT NULL DEFAULT 1 AFTER order_item_id,
    ADD UNIQUE KEY uk_verification_item_ticket (order_item_id, ticket_no),
    ADD INDEX idx_verification_status (status);
