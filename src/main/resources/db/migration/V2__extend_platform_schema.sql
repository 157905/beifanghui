-- 北方汇生活平台扩展模型（MySQL 8.0+）。
-- 覆盖权限、会员、地址、运营内容、订单幂等、退款、库存流水、审批、商城履约和消息。

ALTER TABLE bf_user
    ADD COLUMN unionid VARCHAR(64) NULL AFTER wechat_openid,
    ADD COLUMN real_name VARCHAR(64) NULL AFTER nickname,
    ADD COLUMN gender TINYINT NOT NULL DEFAULT 0 AFTER avatar_url,
    ADD COLUMN last_login_at DATETIME NULL AFTER status,
    ADD UNIQUE KEY uk_user_unionid (unionid),
    ADD INDEX idx_user_mobile (mobile),
    ADD CONSTRAINT chk_user_gender CHECK (gender IN (0, 1, 2));

CREATE TABLE bf_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) NOT NULL DEFAULT 'API',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

CREATE TABLE bf_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES bf_role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES bf_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

CREATE TABLE bf_member_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    level_code VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    points INT NOT NULL DEFAULT 0,
    balance_cent BIGINT NOT NULL DEFAULT 0,
    growth_value INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_user (user_id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT chk_member_points CHECK (points >= 0),
    CONSTRAINT chk_member_balance CHECK (balance_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员账户';

CREATE TABLE bf_user_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_mobile VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    postal_code VARCHAR(16),
    is_default TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_address_user_deleted (user_id, deleted),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址';

CREATE TABLE bf_resource_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    image_type VARCHAR(20) NOT NULL DEFAULT 'DETAIL',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_resource_image_sort (resource_id, sort_order),
    CONSTRAINT fk_resource_image_resource FOREIGN KEY (resource_id) REFERENCES bf_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源图片';

CREATE TABLE bf_inventory_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inventory_id BIGINT NOT NULL,
    order_no VARCHAR(32),
    change_type VARCHAR(32) NOT NULL,
    quantity_delta INT NOT NULL,
    quantity_after INT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inventory_log_idempotency (idempotency_key),
    INDEX idx_inventory_log_inventory_time (inventory_id, created_at),
    CONSTRAINT fk_inventory_log_inventory FOREIGN KEY (inventory_id) REFERENCES bf_inventory(id),
    CONSTRAINT chk_inventory_log_after CHECK (quantity_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变更流水';

ALTER TABLE bf_order
    ADD COLUMN client_request_id VARCHAR(64) NULL AFTER user_id,
    ADD COLUMN discount_amount_cent BIGINT NOT NULL DEFAULT 0 AFTER total_amount_cent,
    ADD COLUMN contact_name VARCHAR(64) NULL AFTER expires_at,
    ADD COLUMN contact_mobile VARCHAR(32) NULL AFTER contact_name,
    ADD COLUMN remark VARCHAR(500) NULL AFTER contact_mobile,
    ADD COLUMN cancelled_at DATETIME NULL AFTER remark,
    ADD COLUMN completed_at DATETIME NULL AFTER cancelled_at,
    ADD UNIQUE KEY uk_order_user_request (user_id, client_request_id),
    ADD INDEX idx_order_status_created (status, created_at),
    ADD CONSTRAINT chk_order_discount CHECK (discount_amount_cent >= 0);

ALTER TABLE bf_order_item
    ADD COLUMN sku_code VARCHAR(64) NULL AFTER sku_id,
    ADD COLUMN sku_name VARCHAR(200) NULL AFTER sku_code,
    ADD COLUMN resource_type VARCHAR(32) NULL AFTER resource_name,
    ADD COLUMN snapshot JSON NULL AFTER time_slot,
    ADD INDEX idx_order_item_order (order_id),
    ADD INDEX idx_order_item_sku_date (sku_id, service_date),
    ADD CONSTRAINT chk_order_item_amount CHECK (unit_price_cent >= 0 AND amount_cent >= 0);

ALTER TABLE bf_payment
    ADD COLUMN callback_payload JSON NULL AFTER paid_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD INDEX idx_payment_order_status (order_id, status);

CREATE TABLE bf_refund (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    refund_no VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    channel_refund_id VARCHAR(128),
    amount_cent BIGINT NOT NULL,
    reason VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    requested_by BIGINT,
    refunded_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_refund_channel_id (channel_refund_id),
    INDEX idx_refund_order_status (order_id, status),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES bf_payment(id),
    CONSTRAINT fk_refund_requester FOREIGN KEY (requested_by) REFERENCES bf_user(id),
    CONSTRAINT chk_refund_amount CHECK (amount_cent > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款流水';

ALTER TABLE bf_verification
    ADD COLUMN valid_from DATETIME NULL AFTER status,
    ADD COLUMN valid_until DATETIME NULL AFTER valid_from,
    ADD COLUMN verify_channel VARCHAR(32) NULL AFTER verified_at,
    ADD COLUMN device_no VARCHAR(64) NULL AFTER verify_channel,
    ADD INDEX idx_verification_item_status (order_item_id, status),
    ADD INDEX idx_verification_status_validity (status, valid_from, valid_until);

CREATE TABLE bf_approval_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    current_step INT NOT NULL DEFAULT 1,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_business (business_type, business_id),
    INDEX idx_approval_applicant_status (applicant_id, status),
    CONSTRAINT fk_approval_applicant FOREIGN KEY (applicant_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议与内部用车审批实例';

CREATE TABLE bf_approval_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL,
    step_no INT NOT NULL,
    approver_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    comment VARCHAR(500),
    handled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_task_step (instance_id, step_no, approver_id),
    INDEX idx_approval_task_approver_status (approver_id, status),
    CONSTRAINT fk_approval_task_instance FOREIGN KEY (instance_id) REFERENCES bf_approval_instance(id),
    CONSTRAINT fk_approval_task_approver FOREIGN KEY (approver_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

CREATE TABLE bf_banner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_value VARCHAR(255),
    start_at DATETIME,
    end_at DATETIME,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_banner_enabled_time_sort (enabled, start_at, end_at, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播';

CREATE TABLE bf_home_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_key VARCHAR(50) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    subtitle VARCHAR(255),
    icon_url VARCHAR(512),
    cover_url VARCHAR(512),
    path VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_home_entry_key (entry_key),
    INDEX idx_home_entry_type_enabled_sort (entry_type, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页快捷入口与特色服务';

CREATE TABLE bf_article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500),
    cover_url VARCHAR(512),
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    pinned TINYINT NOT NULL DEFAULT 0,
    published_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_article_type_status_publish (article_type, status, pinned, published_at),
    CONSTRAINT fk_article_creator FOREIGN KEY (created_by) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告与资讯';

CREATE TABLE bf_platform_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    value_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    is_public TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(255),
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_setting_public (is_public),
    CONSTRAINT fk_setting_updater FOREIGN KEY (updated_by) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台参数';

CREATE TABLE bf_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    contact VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reply VARCHAR(500),
    handled_by BIGINT,
    handled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_status_created (status, created_at),
    INDEX idx_feedback_user_created (user_id, created_at),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_feedback_handler FOREIGN KEY (handled_by) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈';

CREATE TABLE bf_cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    selected TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_cart_sku FOREIGN KEY (sku_id) REFERENCES bf_resource_sku(id),
    CONSTRAINT chk_cart_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城购物车';

CREATE TABLE bf_shipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    carrier_code VARCHAR(32),
    carrier_name VARCHAR(64),
    tracking_no VARCHAR(64),
    receiver_snapshot JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    shipped_at DATETIME,
    received_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shipment_order (order_id),
    INDEX idx_shipment_tracking (tracking_no),
    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES bf_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城物流';

CREATE TABLE bf_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    business_type VARCHAR(32),
    business_id BIGINT,
    read_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_user_read_time (user_id, read_at, created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

ALTER TABLE bf_audit_log
    ADD COLUMN ip_address VARCHAR(64) NULL AFTER detail,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER ip_address,
    ADD INDEX idx_audit_operator_time (operator_id, created_at),
    ADD INDEX idx_audit_target (target_type, target_id);
