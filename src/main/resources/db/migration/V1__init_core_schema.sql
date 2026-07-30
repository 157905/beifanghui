-- 北方汇生活核心交易模型。执行前请创建 UTF-8MB4 的 beifanghui 数据库。

CREATE TABLE bf_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_openid VARCHAR(64) UNIQUE,
    mobile VARCHAR(32),
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES bf_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_type VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    cover_url VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    attributes JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resource_type_status (resource_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_resource_sku (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    price_cent INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    attributes JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sku_resource FOREIGN KEY (resource_id) REFERENCES bf_resource(id),
    CHECK (price_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    time_slot VARCHAR(64) NOT NULL DEFAULT '',
    total_quantity INT NOT NULL,
    available_quantity INT NOT NULL,
    price_cent INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_inventory_slot (sku_id, business_date, time_slot),
    CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES bf_resource_sku(id),
    CHECK (total_quantity >= 0),
    CHECK (available_quantity >= 0),
    CHECK (price_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    order_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount_cent INT NOT NULL,
    payable_amount_cent INT NOT NULL,
    paid_amount_cent INT NOT NULL DEFAULT 0,
    expires_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    INDEX idx_order_user_status (user_id, status),
    CHECK (total_amount_cent >= 0),
    CHECK (payable_amount_cent >= 0),
    CHECK (paid_amount_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    resource_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price_cent INT NOT NULL,
    amount_cent INT NOT NULL,
    service_date DATE,
    time_slot VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES bf_resource_sku(id),
    CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    payment_no VARCHAR(32) NOT NULL UNIQUE,
    channel VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(128) UNIQUE,
    amount_cent INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    paid_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CHECK (amount_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_verification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    verification_code_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
    verified_by BIGINT,
    verified_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verification_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT fk_verification_operator FOREIGN KEY (verified_by) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bf_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    detail JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
