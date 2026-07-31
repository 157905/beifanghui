-- 用户退款申请、管理员审核及渠道回调状态。
ALTER TABLE bf_refund
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER requested_by,
    ADD COLUMN reviewed_by BIGINT NULL AFTER idempotency_key,
    ADD COLUMN review_comment VARCHAR(500) NULL AFTER reviewed_by,
    ADD COLUMN reviewed_at DATETIME NULL AFTER review_comment,
    ADD COLUMN failure_reason VARCHAR(500) NULL AFTER reviewed_at,
    ADD COLUMN previous_order_status VARCHAR(32) NULL AFTER failure_reason,
    ADD UNIQUE KEY uk_refund_request_idempotency (requested_by, idempotency_key),
    ADD INDEX idx_refund_status_created (status, created_at),
    ADD CONSTRAINT fk_refund_reviewer FOREIGN KEY (reviewed_by) REFERENCES bf_user(id);

CREATE TABLE bf_refund_status_log (
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
