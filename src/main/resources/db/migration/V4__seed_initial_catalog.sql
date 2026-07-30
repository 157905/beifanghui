-- 第一批可运行资源目录数据。稳定编码可防止重复，后续由管理端维护。
INSERT INTO bf_business_site (site_code, name, site_type, address, service_phone, status)
SELECT 'NORTH_HOTEL', '北方宾馆', 'HOTEL', '待采购方确认', '400-000-0000', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM bf_business_site WHERE site_code = 'NORTH_HOTEL');

INSERT INTO bf_resource (site_id, resource_type, category_code, name, description, status, attributes)
SELECT s.id, 'HOTEL_ROOM', 'STANDARD_ROOM', '北方宾馆标准间', '第一阶段联调资源，正式房型和规则由管理端维护。', 'ACTIVE', JSON_OBJECT('capacity', 2)
FROM bf_business_site s WHERE s.site_code = 'NORTH_HOTEL'
AND NOT EXISTS (SELECT 1 FROM bf_resource WHERE name = '北方宾馆标准间' AND resource_type = 'HOTEL_ROOM');

INSERT INTO bf_resource (site_id, resource_type, category_code, name, description, status, attributes)
SELECT s.id, 'MEETING_ROOM', 'MEDIUM_MEETING', '北方宾馆中型会议室', '支持内部审批和外部预约的联调资源。', 'ACTIVE', JSON_OBJECT('capacity', 30)
FROM bf_business_site s WHERE s.site_code = 'NORTH_HOTEL'
AND NOT EXISTS (SELECT 1 FROM bf_resource WHERE name = '北方宾馆中型会议室' AND resource_type = 'MEETING_ROOM');

INSERT INTO bf_resource_sku (resource_id, sku_code, name, price_cent, status, attributes)
SELECT r.id, 'HOTEL_STANDARD_001', '标准间一晚', 29800, 'ACTIVE', JSON_OBJECT('unit', 'NIGHT')
FROM bf_resource r WHERE r.name = '北方宾馆标准间'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code = 'HOTEL_STANDARD_001');

INSERT INTO bf_resource_sku (resource_id, sku_code, name, price_cent, status, attributes)
SELECT r.id, 'MEETING_MEDIUM_AM', '中型会议室上午场', 80000, 'ACTIVE', JSON_OBJECT('unit', 'SESSION')
FROM bf_resource r WHERE r.name = '北方宾馆中型会议室'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code = 'MEETING_MEDIUM_AM');

INSERT INTO bf_inventory (sku_id, business_date, time_slot, total_quantity, available_quantity, price_cent)
SELECT sku.id, '2026-07-30', '', 10, 10, 29800 FROM bf_resource_sku sku
WHERE sku.sku_code = 'HOTEL_STANDARD_001'
AND NOT EXISTS (SELECT 1 FROM bf_inventory i WHERE i.sku_id = sku.id AND i.business_date = '2026-07-30' AND i.time_slot = '');

INSERT INTO bf_inventory (sku_id, business_date, time_slot, total_quantity, available_quantity, price_cent)
SELECT sku.id, '2026-07-30', '08:30-12:00', 1, 1, 80000 FROM bf_resource_sku sku
WHERE sku.sku_code = 'MEETING_MEDIUM_AM'
AND NOT EXISTS (SELECT 1 FROM bf_inventory i WHERE i.sku_id = sku.id AND i.business_date = '2026-07-30' AND i.time_slot = '08:30-12:00');
