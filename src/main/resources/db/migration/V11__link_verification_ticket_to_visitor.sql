-- 实名电子票与入园人一一关联，支持工作人员按身份证核验入园。
ALTER TABLE bf_verification
    ADD COLUMN order_person_id BIGINT NULL AFTER order_item_id,
    ADD INDEX idx_verification_order_person (order_person_id),
    ADD CONSTRAINT fk_verification_order_person FOREIGN KEY (order_person_id) REFERENCES bf_order_person(id);
