-- 套票订单保存组件快照，并让每个套票组件拥有独立电子票。
CREATE TABLE bf_order_package_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    component_sku_id BIGINT NOT NULL,
    component_sku_code VARCHAR(64) NOT NULL,
    component_sku_name VARCHAR(200) NOT NULL,
    component_resource_name VARCHAR(200) NOT NULL,
    component_resource_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_package_component (order_item_id, component_sku_id),
    INDEX idx_order_package_item_order_item (order_item_id),
    CONSTRAINT fk_order_package_item_order_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT fk_order_package_item_sku FOREIGN KEY (component_sku_id) REFERENCES bf_resource_sku(id),
    CONSTRAINT chk_order_package_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套票购买时的组件权益快照';

ALTER TABLE bf_verification
    DROP INDEX uk_verification_item_ticket,
    ADD COLUMN order_package_item_id BIGINT NULL AFTER order_person_id,
    ADD INDEX idx_verification_order_package_item (order_package_item_id),
    ADD UNIQUE KEY uk_verification_scope_ticket (order_item_id, order_package_item_id, ticket_no),
    ADD CONSTRAINT fk_verification_order_package_item
        FOREIGN KEY (order_package_item_id) REFERENCES bf_order_package_item(id);
