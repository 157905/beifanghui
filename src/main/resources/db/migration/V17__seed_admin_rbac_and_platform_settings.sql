-- 管理平台基础角色、模块权限和可维护参数。
INSERT INTO bf_role(code,name) VALUES
('ADMIN','系统管理员'),('OPERATIONS','运营人员'),('VERIFIER','核销人员')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO bf_permission(code,name,permission_type) VALUES
('dashboard.view','查看运营概览','MENU'),
('catalog.manage','管理资源与库存','MENU'),
('orders.view','查看订单','MENU'),
('refunds.review','审核退款','MENU'),
('verification.use','执行电子核销','MENU'),
('users.manage','管理用户与会员','MENU'),
('content.manage','管理运营内容','MENU'),
('reports.view','查看数据报表','MENU'),
('settings.manage','管理系统设置','MENU')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission_type=VALUES(permission_type);

INSERT IGNORE INTO bf_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM bf_role r CROSS JOIN bf_permission p WHERE r.code='ADMIN';

INSERT IGNORE INTO bf_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM bf_role r CROSS JOIN bf_permission p
WHERE r.code='OPERATIONS' AND p.code IN
('dashboard.view','catalog.manage','orders.view','refunds.review','users.manage','content.manage','reports.view');

INSERT IGNORE INTO bf_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM bf_role r CROSS JOIN bf_permission p
WHERE r.code='VERIFIER' AND p.code IN ('dashboard.view','verification.use');

INSERT INTO bf_platform_setting(setting_key,setting_value,value_type,is_public,description) VALUES
('platform.name','北方汇生活','STRING',1,'小程序和管理端展示的平台名称'),
('service.hotline','400-000-0000','STRING',1,'用户端展示的客服电话'),
('order.payment_timeout_minutes','15','INTEGER',0,'待支付订单自动超时分钟数'),
('refund.manual_review_enabled','true','BOOLEAN',0,'退款是否进入人工审核流程'),
('content.default_page_size','20','INTEGER',0,'内容列表默认分页条数')
ON DUPLICATE KEY UPDATE description=VALUES(description);
