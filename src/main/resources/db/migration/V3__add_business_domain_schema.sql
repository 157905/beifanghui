-- 按《“北方汇生活”微信小程序项目技术文本》《具体要求》补齐七大业务领域。
-- MySQL 8.0+；金额单位均为分；实名信息仅保存应用层加密密文与 SHA-256 摘要。

ALTER TABLE bf_resource_sku MODIFY COLUMN price_cent BIGINT NOT NULL;
ALTER TABLE bf_inventory MODIFY COLUMN price_cent BIGINT NOT NULL;
ALTER TABLE bf_order
    MODIFY COLUMN total_amount_cent BIGINT NOT NULL,
    MODIFY COLUMN payable_amount_cent BIGINT NOT NULL,
    MODIFY COLUMN paid_amount_cent BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bf_order_item
    MODIFY COLUMN unit_price_cent BIGINT NOT NULL,
    MODIFY COLUMN amount_cent BIGINT NOT NULL;
ALTER TABLE bf_payment MODIFY COLUMN amount_cent BIGINT NOT NULL;

CREATE TABLE bf_business_site (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    site_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    site_type VARCHAR(32) NOT NULL,
    address VARCHAR(255),
    longitude DECIMAL(10,7),
    latitude DECIMAL(10,7),
    service_phone VARCHAR(32),
    introduction TEXT,
    cover_url VARCHAR(512),
    delivery_enabled TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_code (site_code),
    INDEX idx_site_type_status (site_type, status),
    CONSTRAINT chk_site_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_site_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营场所与地图导航';

ALTER TABLE bf_resource
    ADD COLUMN site_id BIGINT NULL AFTER id,
    ADD COLUMN category_code VARCHAR(50) NULL AFTER resource_type,
    ADD INDEX idx_resource_site_type_status (site_id, resource_type, status),
    ADD CONSTRAINT fk_resource_site FOREIGN KEY (site_id) REFERENCES bf_business_site(id);

CREATE TABLE bf_price_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    start_date DATE,
    end_date DATE,
    day_of_week_mask CHAR(7),
    start_time TIME,
    end_time TIME,
    price_cent BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_price_rule_sku_enabled_date (sku_id, enabled, start_date, end_date, priority),
    CONSTRAINT fk_price_rule_sku FOREIGN KEY (sku_id) REFERENCES bf_resource_sku(id),
    CONSTRAINT chk_price_rule_date CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_price_rule_price CHECK (price_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节假日、淡旺季、时段与会员价格规则';

CREATE TABLE bf_resource_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price_cent BIGINT NOT NULL,
    start_at DATETIME,
    end_at DATETIME,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_package_code (package_code),
    INDEX idx_package_status_time (status, start_at, end_at),
    CONSTRAINT chk_package_price CHECK (price_cent >= 0),
    CONSTRAINT chk_package_time CHECK (end_at IS NULL OR start_at IS NULL OR end_at >= start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='住宿餐饮会议组合及景区套票';

CREATE TABLE bf_resource_package_item (
    package_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (package_id, sku_id),
    CONSTRAINT fk_package_item_package FOREIGN KEY (package_id) REFERENCES bf_resource_package(id),
    CONSTRAINT fk_package_item_sku FOREIGN KEY (sku_id) REFERENCES bf_resource_sku(id),
    CONSTRAINT chk_package_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐明细';

CREATE TABLE bf_order_person (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    person_type VARCHAR(32) NOT NULL,
    name_cipher VARBINARY(512) NOT NULL,
    mobile_cipher VARBINARY(512),
    id_type VARCHAR(20),
    id_no_cipher VARBINARY(512),
    id_no_hash CHAR(64),
    employee_no_hash CHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_person_item (order_item_id),
    INDEX idx_order_person_id_hash (id_no_hash),
    CONSTRAINT fk_order_person_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入住人、入园人、体验人实名信息（加密）';

CREATE TABLE bf_hotel_booking (
    order_item_id BIGINT PRIMARY KEY,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    room_count INT NOT NULL DEFAULT 1,
    guest_count INT NOT NULL DEFAULT 1,
    actual_check_in_at DATETIME,
    actual_check_out_at DATETIME,
    settlement_amount_cent BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hotel_booking_dates (check_in_date, check_out_date),
    CONSTRAINT fk_hotel_booking_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT chk_hotel_booking_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_hotel_booking_count CHECK (room_count > 0 AND guest_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='住宿预订与入住退房';

CREATE TABLE bf_meeting_booking (
    order_item_id BIGINT PRIMARY KEY,
    attendee_count INT NOT NULL,
    meeting_subject VARCHAR(200),
    internal_booking TINYINT NOT NULL DEFAULT 0,
    usage_started_at DATETIME,
    usage_ended_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_meeting_booking_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT chk_meeting_attendees CHECK (attendee_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室预订与使用记录';

CREATE TABLE bf_dish_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    site_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dish_category_site_name (site_id, name),
    INDEX idx_dish_category_site_enabled_sort (site_id, enabled, sort_order),
    CONSTRAINT fk_dish_category_site FOREIGN KEY (site_id) REFERENCES bf_business_site(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐品分类';

CREATE TABLE bf_dish (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    dish_code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    introduction VARCHAR(500),
    image_url VARCHAR(512),
    price_cent BIGINT NOT NULL,
    stock_quantity INT,
    delivery_minutes INT,
    featured TINYINT NOT NULL DEFAULT 0,
    special_price_cent BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dish_code (dish_code),
    INDEX idx_dish_category_status (category_id, status),
    CONSTRAINT fk_dish_category FOREIGN KEY (category_id) REFERENCES bf_dish_category(id),
    CONSTRAINT chk_dish_price CHECK (price_cent >= 0 AND (special_price_cent IS NULL OR special_price_cent >= 0)),
    CONSTRAINT chk_dish_stock CHECK (stock_quantity IS NULL OR stock_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品、套餐与可配送餐品';

CREATE TABLE bf_dining_booking (
    order_item_id BIGINT PRIMARY KEY,
    diner_count INT NOT NULL,
    reservation_fee_cent BIGINT NOT NULL DEFAULT 5000,
    reservation_fee_deductible TINYINT NOT NULL DEFAULT 1,
    kitchen_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dining_booking_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT chk_dining_count CHECK (diner_count > 0),
    CONSTRAINT chk_dining_fee CHECK (reservation_fee_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐桌雅间预订';

CREATE TABLE bf_order_dish (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    dish_name VARCHAR(100) NOT NULL,
    unit_price_cent BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount_cent BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_dish_order (order_id),
    CONSTRAINT fk_order_dish_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CONSTRAINT fk_order_dish_dish FOREIGN KEY (dish_id) REFERENCES bf_dish(id),
    CONSTRAINT chk_order_dish_values CHECK (quantity > 0 AND unit_price_cent >= 0 AND amount_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐饮订单菜品快照';

CREATE TABLE bf_delivery_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    delivery_location VARCHAR(255) NOT NULL,
    delivery_fee_cent BIGINT NOT NULL DEFAULT 500,
    free_delivery_threshold_cent BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING_ACCEPT',
    accept_deadline DATETIME,
    accepted_by BIGINT,
    accepted_at DATETIME,
    delivered_at DATETIME,
    received_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_delivery_order (order_id),
    INDEX idx_delivery_status_deadline (status, accept_deadline),
    CONSTRAINT fk_delivery_order_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CONSTRAINT fk_delivery_order_site FOREIGN KEY (site_id) REFERENCES bf_business_site(id),
    CONSTRAINT fk_delivery_order_acceptor FOREIGN KEY (accepted_by) REFERENCES bf_user(id),
    CONSTRAINT chk_delivery_fee CHECK (delivery_fee_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='北方宾馆场内餐饮配送';

CREATE TABLE bf_member_level (
    level_code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    min_growth_value INT NOT NULL DEFAULT 0,
    discount_rate DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    benefits JSON,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_member_level_growth CHECK (min_growth_value >= 0),
    CONSTRAINT chk_member_level_discount CHECK (discount_rate > 0 AND discount_rate <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通、白银、黄金等会员等级规则';

CREATE TABLE bf_member_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_account_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    balance_delta_cent BIGINT NOT NULL DEFAULT 0,
    points_delta INT NOT NULL DEFAULT 0,
    balance_after_cent BIGINT NOT NULL,
    points_after INT NOT NULL,
    business_type VARCHAR(32),
    business_id BIGINT,
    idempotency_key VARCHAR(100) NOT NULL,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_ledger_idempotency (idempotency_key),
    INDEX idx_member_ledger_account_time (member_account_id, created_at),
    CONSTRAINT fk_member_ledger_account FOREIGN KEY (member_account_id) REFERENCES bf_member_account(id),
    CONSTRAINT chk_member_ledger_after CHECK (balance_after_cent >= 0 AND points_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员充值、消费、退款和积分流水';

CREATE TABLE bf_member_recharge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recharge_no VARCHAR(32) NOT NULL,
    member_account_id BIGINT NOT NULL,
    amount_cent BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    channel_transaction_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    paid_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recharge_no (recharge_no),
    UNIQUE KEY uk_recharge_channel_tx (channel_transaction_id),
    INDEX idx_recharge_account_status (member_account_id, status),
    CONSTRAINT fk_recharge_account FOREIGN KEY (member_account_id) REFERENCES bf_member_account(id),
    CONSTRAINT chk_recharge_amount CHECK (amount_cent > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员充值单';

CREATE TABLE bf_ticket_profile (
    sku_id BIGINT PRIMARY KEY,
    audience_rule VARCHAR(500),
    usage_rule TEXT NOT NULL,
    refund_rule TEXT,
    entry_notice TEXT,
    valid_days INT NOT NULL DEFAULT 1,
    max_per_order INT,
    real_name_required TINYINT NOT NULL DEFAULT 0,
    id_card_required TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_profile_sku FOREIGN KEY (sku_id) REFERENCES bf_resource_sku(id),
    CONSTRAINT chk_ticket_valid_days CHECK (valid_days > 0),
    CONSTRAINT chk_ticket_max_order CHECK (max_per_order IS NULL OR max_per_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区、浴都、体育馆和体验项目票务规则';

CREATE TABLE bf_guide_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    site_id BIGINT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    media_url VARCHAR(512),
    longitude DECIMAL(10,7),
    latitude DECIMAL(10,7),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_guide_site_type_status_sort (site_id, content_type, status, sort_order),
    CONSTRAINT fk_guide_site FOREIGN KEY (site_id) REFERENCES bf_business_site(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区地图、景点、语音导览和游玩攻略';

CREATE TABLE bf_vehicle_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    seat_count INT NOT NULL,
    fuel_type VARCHAR(20) NOT NULL DEFAULT 'GASOLINE_OR_DIESEL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_model_code (model_code),
    CONSTRAINT chk_vehicle_model_seats CHECK (seat_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车队车型';

CREATE TABLE bf_vehicle (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_id BIGINT NOT NULL,
    vehicle_no VARCHAR(32) NOT NULL,
    plate_no VARCHAR(32) NOT NULL,
    image_url VARCHAR(512),
    configuration JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    mileage_km DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_no (vehicle_no),
    UNIQUE KEY uk_vehicle_plate (plate_no),
    INDEX idx_vehicle_model_status (model_id, status),
    CONSTRAINT fk_vehicle_model FOREIGN KEY (model_id) REFERENCES bf_vehicle_model(id),
    CONSTRAINT chk_vehicle_mileage CHECK (mileage_km >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆档案与可用状态';

CREATE TABLE bf_driver (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    driver_no VARCHAR(32) NOT NULL,
    name_cipher VARBINARY(512) NOT NULL,
    mobile_cipher VARBINARY(512) NOT NULL,
    license_no_cipher VARBINARY(512) NOT NULL,
    license_no_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_driver_no (driver_no),
    UNIQUE KEY uk_driver_license_hash (license_no_hash),
    UNIQUE KEY uk_driver_user (user_id),
    CONSTRAINT fk_driver_user FOREIGN KEY (user_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='司机档案（敏感字段加密）';

CREATE TABLE bf_vehicle_tariff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_id BIGINT NOT NULL,
    tariff_name VARCHAR(100) NOT NULL DEFAULT '北重车队统一定价',
    bare_month_cent BIGINT,
    half_day_cent BIGINT,
    full_day_cent BIGINT,
    month_cent BIGINT,
    overtime_cent_per_hour BIGINT,
    over_distance_cent_per_km BIGINT,
    half_day_limit_hours INT,
    half_day_limit_km INT,
    full_day_limit_hours INT,
    full_day_limit_km INT,
    month_limit_km INT,
    temporary_rule TEXT,
    effective_from DATE NOT NULL,
    effective_to DATE,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_tariff_model_date (model_id, effective_from),
    INDEX idx_vehicle_tariff_enabled_date (enabled, effective_from, effective_to),
    CONSTRAINT fk_vehicle_tariff_model FOREIGN KEY (model_id) REFERENCES bf_vehicle_model(id),
    CONSTRAINT chk_vehicle_tariff_date CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='半天、全天、月租、裸租及超时超公里计价';

CREATE TABLE bf_vehicle_booking (
    order_item_id BIGINT PRIMARY KEY,
    business_mode VARCHAR(32) NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    departure VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    passenger_count INT NOT NULL,
    estimated_km DECIMAL(10,2),
    rental_mode VARCHAR(32),
    dispatch_deadline DATETIME,
    deposit_amount_cent BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vehicle_booking_time (start_at, end_at),
    INDEX idx_vehicle_booking_mode (business_mode),
    CONSTRAINT fk_vehicle_booking_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT chk_vehicle_booking_time CHECK (end_at > start_at),
    CONSTRAINT chk_vehicle_booking_passengers CHECK (passenger_count > 0),
    CONSTRAINT chk_vehicle_booking_deposit CHECK (deposit_amount_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部用车与社会租赁预订';

CREATE TABLE bf_vehicle_dispatch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    driver_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING_DRIVER',
    accepted_at DATETIME,
    departed_at DATETIME,
    completed_at DATETIME,
    current_longitude DECIMAL(10,7),
    current_latitude DECIMAL(10,7),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_dispatch_item (order_item_id),
    INDEX idx_vehicle_dispatch_driver_status (driver_id, status),
    INDEX idx_vehicle_dispatch_vehicle_status (vehicle_id, status),
    CONSTRAINT fk_vehicle_dispatch_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT fk_vehicle_dispatch_vehicle FOREIGN KEY (vehicle_id) REFERENCES bf_vehicle(id),
    CONSTRAINT fk_vehicle_dispatch_driver FOREIGN KEY (driver_id) REFERENCES bf_driver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆派单、司机接单和当前位置';

CREATE TABLE bf_vehicle_trip_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispatch_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_at DATETIME NOT NULL,
    longitude DECIMAL(10,7),
    latitude DECIMAL(10,7),
    mileage_km DECIMAL(10,2),
    fuel_liter DECIMAL(10,2),
    detail JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trip_log_dispatch_time (dispatch_id, event_at),
    CONSTRAINT fk_trip_log_dispatch FOREIGN KEY (dispatch_id) REFERENCES bf_vehicle_dispatch(id),
    CONSTRAINT chk_trip_log_values CHECK ((mileage_km IS NULL OR mileage_km >= 0) AND (fuel_liter IS NULL OR fuel_liter >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆轨迹、里程、油耗与交还车记录';

CREATE TABLE bf_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    coupon_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    coupon_type VARCHAR(32) NOT NULL,
    discount_amount_cent BIGINT,
    discount_rate DECIMAL(5,4),
    threshold_amount_cent BIGINT NOT NULL DEFAULT 0,
    applicable_resource_type VARCHAR(32),
    issue_total INT,
    per_user_limit INT NOT NULL DEFAULT 1,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_coupon_code (coupon_code),
    INDEX idx_coupon_status_time (status, start_at, end_at),
    CONSTRAINT chk_coupon_time CHECK (end_at > start_at),
    CONSTRAINT chk_coupon_values CHECK (threshold_amount_cent >= 0 AND per_user_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满减、折扣和优惠券活动';

CREATE TABLE bf_user_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
    order_id BIGINT,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME,
    UNIQUE KEY uk_user_coupon_order (order_id),
    INDEX idx_user_coupon_user_status (user_id, status),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES bf_coupon(id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_user_coupon_order FOREIGN KEY (order_id) REFERENCES bf_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券领取与核销';

CREATE TABLE bf_after_sale (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    after_sale_no VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT,
    user_id BIGINT NOT NULL,
    after_sale_type VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    evidence JSON,
    requested_amount_cent BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    handled_by BIGINT,
    handled_at DATETIME,
    result_note VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_after_sale_no (after_sale_no),
    INDEX idx_after_sale_order_status (order_id, status),
    INDEX idx_after_sale_user_time (user_id, created_at),
    CONSTRAINT fk_after_sale_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CONSTRAINT fk_after_sale_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT fk_after_sale_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_after_sale_handler FOREIGN KEY (handled_by) REFERENCES bf_user(id),
    CONSTRAINT chk_after_sale_amount CHECK (requested_amount_cent IS NULL OR requested_amount_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款、退换货、投诉等售后申请';

CREATE TABLE bf_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    score TINYINT NOT NULL,
    content VARCHAR(1000),
    images JSON,
    anonymous TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    merchant_reply VARCHAR(1000),
    replied_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_review_order_item (order_item_id),
    INDEX idx_review_resource_status_time (resource_id, status, created_at),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_item_id) REFERENCES bf_order_item(id),
    CONSTRAINT fk_review_resource FOREIGN KEY (resource_id) REFERENCES bf_resource(id),
    CONSTRAINT chk_review_score CHECK (score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品、司机和服务评价';

CREATE TABLE bf_order_status_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    operator_id BIGINT,
    reason VARCHAR(255),
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_status_idempotency (idempotency_key),
    INDEX idx_order_status_log_order_time (order_id, created_at),
    CONSTRAINT fk_order_status_log_order FOREIGN KEY (order_id) REFERENCES bf_order(id),
    CONSTRAINT fk_order_status_log_operator FOREIGN KEY (operator_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态机轨迹';

CREATE TABLE bf_article_favorite (
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, article_id),
    CONSTRAINT fk_article_favorite_user FOREIGN KEY (user_id) REFERENCES bf_user(id),
    CONSTRAINT fk_article_favorite_article FOREIGN KEY (article_id) REFERENCES bf_article(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯收藏';

CREATE TABLE bf_notification_setting (
    user_id BIGINT PRIMARY KEY,
    platform_enabled TINYINT NOT NULL DEFAULT 1,
    sms_enabled TINYINT NOT NULL DEFAULT 1,
    marketing_enabled TINYINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_id) REFERENCES bf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知偏好';

-- 《具体要求》第 7-8 页给出的北重车队收费标准（元换算为分）。
INSERT INTO bf_vehicle_model (model_code, name, seat_count) VALUES
('SEDAN', '小轿车', 5), ('GL8', '商务车（GL8）', 7), ('PRADO', '越野车（PRADO）', 5),
('JINBEI', '中型客车（金杯）', 12), ('COASTER', '中型客车（考斯特）', 23),
('BUS_35', '35座客车（公交）', 35), ('BUS_39', '39座客车', 39),
('BUS_51', '51座客车', 51), ('HIACE_12', '12座客车（丰田海狮）', 12);

INSERT INTO bf_vehicle_tariff
(model_id, bare_month_cent, half_day_cent, full_day_cent, month_cent, overtime_cent_per_hour,
 over_distance_cent_per_km, half_day_limit_hours, half_day_limit_km, full_day_limit_hours,
 full_day_limit_km, month_limit_km, temporary_rule, effective_from)
SELECT id,
 CASE model_code WHEN 'SEDAN' THEN 650000 WHEN 'HIACE_12' THEN 150000 END,
 CASE model_code WHEN 'SEDAN' THEN 28000 WHEN 'GL8' THEN 38000 WHEN 'PRADO' THEN 40000 WHEN 'JINBEI' THEN 70000 WHEN 'COASTER' THEN 80000 WHEN 'BUS_35' THEN 60000 WHEN 'BUS_39' THEN 80000 WHEN 'BUS_51' THEN 100000 END,
 CASE model_code WHEN 'SEDAN' THEN 55000 WHEN 'GL8' THEN 76000 WHEN 'PRADO' THEN 80000 WHEN 'JINBEI' THEN 100000 WHEN 'COASTER' THEN 120000 WHEN 'BUS_35' THEN 100000 WHEN 'BUS_39' THEN 120000 WHEN 'BUS_51' THEN 150000 END,
 CASE model_code WHEN 'SEDAN' THEN 980000 WHEN 'GL8' THEN 1200000 END,
 CASE model_code WHEN 'SEDAN' THEN 5000 WHEN 'GL8' THEN 5000 WHEN 'PRADO' THEN 5000 WHEN 'JINBEI' THEN 6000 WHEN 'COASTER' THEN 6000 WHEN 'BUS_35' THEN 6000 WHEN 'BUS_39' THEN 10000 WHEN 'BUS_51' THEN 12000 END,
 CASE model_code WHEN 'SEDAN' THEN 200 WHEN 'GL8' THEN 200 WHEN 'PRADO' THEN 300 WHEN 'JINBEI' THEN 300 WHEN 'COASTER' THEN 300 WHEN 'BUS_35' THEN 300 WHEN 'BUS_39' THEN 400 WHEN 'BUS_51' THEN 400 END,
 CASE WHEN model_code NOT IN ('HIACE_12') THEN 4 END,
 CASE WHEN model_code IN ('SEDAN','GL8','PRADO') THEN 150 WHEN model_code NOT IN ('HIACE_12') THEN 100 END,
 CASE WHEN model_code NOT IN ('HIACE_12') THEN 8 END,
 CASE WHEN model_code IN ('SEDAN','GL8','PRADO') THEN 300 WHEN model_code NOT IN ('HIACE_12') THEN 200 END,
 CASE WHEN model_code = 'SEDAN' THEN 4000 END,
 CASE WHEN model_code = 'SEDAN' THEN '临租不设起步价，50公里或3小时内按每公里2元；超出后不满半天按半天、超半天不满全天按全天计费。' END,
 '2026-01-01'
FROM bf_vehicle_model;

INSERT INTO bf_member_level (level_code, name, min_growth_value, discount_rate) VALUES
('NORMAL', '普通会员', 0, 1.0000), ('SILVER', '白银会员', 1000, 1.0000), ('GOLD', '黄金会员', 5000, 1.0000);
