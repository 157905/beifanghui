-- 补偿 V15 在开发热重载期间提前执行的差异。
-- 本迁移可同时兼容：已具有最终 V15 结构的测试库，以及只执行了早期 V15 结构的开发库。
SET @previous_status_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bf_refund'
      AND column_name = 'previous_order_status'
);
SET @add_previous_status_sql = IF(
    @previous_status_column_exists = 0,
    'ALTER TABLE bf_refund ADD COLUMN previous_order_status VARCHAR(32) NULL AFTER failure_reason',
    'SELECT 1'
);
PREPARE add_previous_status_statement FROM @add_previous_status_sql;
EXECUTE add_previous_status_statement;
DEALLOCATE PREPARE add_previous_status_statement;

CREATE TABLE IF NOT EXISTS bf_refund_status_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    refund_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    operator_id BIGINT NULL,
    note VARCHAR(500) NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_status_log_idempotency (idempotency_key),
    INDEX idx_refund_status_log_refund_time (refund_id, created_at),
    CONSTRAINT fk_refund_status_log_refund FOREIGN KEY (refund_id) REFERENCES bf_refund(id),
    CONSTRAINT fk_refund_status_log_operator FOREIGN KEY (operator_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请状态流水';
