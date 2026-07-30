-- 文旅景区预订模块开发数据：5个景区、5个票种及未来31天库存。
-- 这些数据仅供开发联调，正式名称、票价、图片和规则应由运营人员确认后维护。

INSERT INTO bf_business_site
(site_code,name,site_type,address,longitude,latitude,service_phone,introduction,status)
VALUES
('SCENIC_WEAPON_CITY','北方兵器城','SCENIC','内蒙古自治区包头市青山区兵工路',109.9060000,40.6820000,'0472-0000001','兵器文化主题景区开发联调数据。','ACTIVE'),
('SCENIC_WUDANGZHAO','五当召','SCENIC','内蒙古自治区包头市石拐区吉忽伦图苏木',110.3770000,40.8010000,'0472-0000002','历史文化景区开发联调数据。','ACTIVE'),
('SCENIC_MEIDAIZHAO','美岱召','SCENIC','内蒙古自治区包头市土默特右旗美岱召镇',110.6910000,40.5930000,'0472-0000003','历史文化景区开发联调数据。','ACTIVE'),
('SCENIC_SAIHANTALA','赛汗塔拉城中草原','SCENIC','内蒙古自治区包头市九原区建设路',109.9450000,40.6380000,'0472-0000004','城市草原景区开发联调数据。','ACTIVE'),
('SCENIC_NANHAI','南海湿地景区','SCENIC','内蒙古自治区包头市东河区南海湿地景区',110.0640000,40.5500000,'0472-0000005','湿地生态景区开发联调数据。','ACTIVE')
ON DUPLICATE KEY UPDATE
name=VALUES(name),site_type=VALUES(site_type),address=VALUES(address),longitude=VALUES(longitude),
latitude=VALUES(latitude),service_phone=VALUES(service_phone),introduction=VALUES(introduction),status='ACTIVE';

INSERT INTO bf_resource
(site_id,resource_type,category_code,name,description,cover_url,status,attributes)
SELECT id,'SCENIC_TICKET','CULTURAL_SCENIC','北方兵器城','兵器文化主题景区，当前内容用于后端开发联调。',
       '/images/scenic/weapon-city.jpg','ACTIVE',JSON_OBJECT('openingHours','09:00-17:30','recommendedDurationMinutes',180)
FROM bf_business_site s WHERE s.site_code='SCENIC_WEAPON_CITY'
AND NOT EXISTS (SELECT 1 FROM bf_resource r WHERE r.site_id=s.id AND r.resource_type='SCENIC_TICKET');

INSERT INTO bf_resource
(site_id,resource_type,category_code,name,description,cover_url,status,attributes)
SELECT id,'SCENIC_TICKET','CULTURAL_SCENIC','五当召','历史文化景区，当前内容用于后端开发联调。',
       '/images/scenic/wudangzhao.jpg','ACTIVE',JSON_OBJECT('openingHours','08:30-17:30','recommendedDurationMinutes',180)
FROM bf_business_site s WHERE s.site_code='SCENIC_WUDANGZHAO'
AND NOT EXISTS (SELECT 1 FROM bf_resource r WHERE r.site_id=s.id AND r.resource_type='SCENIC_TICKET');

INSERT INTO bf_resource
(site_id,resource_type,category_code,name,description,cover_url,status,attributes)
SELECT id,'SCENIC_TICKET','CULTURAL_SCENIC','美岱召','历史文化景区，当前内容用于后端开发联调。',
       '/images/scenic/meidaizhao.jpg','ACTIVE',JSON_OBJECT('openingHours','09:00-17:00','recommendedDurationMinutes',150)
FROM bf_business_site s WHERE s.site_code='SCENIC_MEIDAIZHAO'
AND NOT EXISTS (SELECT 1 FROM bf_resource r WHERE r.site_id=s.id AND r.resource_type='SCENIC_TICKET');

INSERT INTO bf_resource
(site_id,resource_type,category_code,name,description,cover_url,status,attributes)
SELECT id,'SCENIC_TICKET','NATURAL_SCENIC','赛汗塔拉城中草原','城市草原景区，当前内容用于后端开发联调。',
       '/images/scenic/saihantala.jpg','ACTIVE',JSON_OBJECT('openingHours','全天开放','recommendedDurationMinutes',120)
FROM bf_business_site s WHERE s.site_code='SCENIC_SAIHANTALA'
AND NOT EXISTS (SELECT 1 FROM bf_resource r WHERE r.site_id=s.id AND r.resource_type='SCENIC_TICKET');

INSERT INTO bf_resource
(site_id,resource_type,category_code,name,description,cover_url,status,attributes)
SELECT id,'SCENIC_TICKET','NATURAL_SCENIC','南海湿地景区','湿地生态景区，当前内容用于后端开发联调。',
       '/images/scenic/nanhai.jpg','ACTIVE',JSON_OBJECT('openingHours','08:30-18:00','recommendedDurationMinutes',180)
FROM bf_business_site s WHERE s.site_code='SCENIC_NANHAI'
AND NOT EXISTS (SELECT 1 FROM bf_resource r WHERE r.site_id=s.id AND r.resource_type='SCENIC_TICKET');

INSERT INTO bf_resource_sku (resource_id,sku_code,name,price_cent,status,attributes)
SELECT r.id,'SCENIC_WEAPON_ADULT_001','成人票',8000,'ACTIVE',JSON_OBJECT('ticketType','ADULT','unit','PERSON')
FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id WHERE s.site_code='SCENIC_WEAPON_CITY'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code='SCENIC_WEAPON_ADULT_001');
INSERT INTO bf_resource_sku (resource_id,sku_code,name,price_cent,status,attributes)
SELECT r.id,'SCENIC_WUDANGZHAO_ADULT_001','成人票',6000,'ACTIVE',JSON_OBJECT('ticketType','ADULT','unit','PERSON')
FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id WHERE s.site_code='SCENIC_WUDANGZHAO'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code='SCENIC_WUDANGZHAO_ADULT_001');
INSERT INTO bf_resource_sku (resource_id,sku_code,name,price_cent,status,attributes)
SELECT r.id,'SCENIC_MEIDAIZHAO_ADULT_001','成人票',3000,'ACTIVE',JSON_OBJECT('ticketType','ADULT','unit','PERSON')
FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id WHERE s.site_code='SCENIC_MEIDAIZHAO'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code='SCENIC_MEIDAIZHAO_ADULT_001');
INSERT INTO bf_resource_sku (resource_id,sku_code,name,price_cent,status,attributes)
SELECT r.id,'SCENIC_SAIHANTALA_SERVICE_001','游览服务票',2000,'ACTIVE',JSON_OBJECT('ticketType','SERVICE','unit','PERSON')
FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id WHERE s.site_code='SCENIC_SAIHANTALA'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code='SCENIC_SAIHANTALA_SERVICE_001');
INSERT INTO bf_resource_sku (resource_id,sku_code,name,price_cent,status,attributes)
SELECT r.id,'SCENIC_NANHAI_ADULT_001','成人票',2000,'ACTIVE',JSON_OBJECT('ticketType','ADULT','unit','PERSON')
FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id WHERE s.site_code='SCENIC_NANHAI'
AND NOT EXISTS (SELECT 1 FROM bf_resource_sku WHERE sku_code='SCENIC_NANHAI_ADULT_001');

INSERT INTO bf_ticket_profile
(sku_id,audience_rule,usage_rule,refund_rule,entry_notice,valid_days,max_per_order,real_name_required,id_card_required)
SELECT id,'适用于成人游客，优惠人群规则以现场公示为准','仅限预约游玩日期使用','未核销前可按平台规则申请整单退款',
       '请携带预约时填写的有效身份证件入园',1,10,1,1
FROM bf_resource_sku
WHERE sku_code IN ('SCENIC_WEAPON_ADULT_001','SCENIC_WUDANGZHAO_ADULT_001','SCENIC_MEIDAIZHAO_ADULT_001',
                   'SCENIC_SAIHANTALA_SERVICE_001','SCENIC_NANHAI_ADULT_001')
ON DUPLICATE KEY UPDATE audience_rule=VALUES(audience_rule),usage_rule=VALUES(usage_rule),
refund_rule=VALUES(refund_rule),entry_notice=VALUES(entry_notice),valid_days=VALUES(valid_days),
max_per_order=VALUES(max_per_order),real_name_required=VALUES(real_name_required),id_card_required=VALUES(id_card_required);

INSERT INTO bf_inventory
(sku_id,business_date,time_slot,total_quantity,available_quantity,price_cent)
SELECT sku.id,DATE_ADD(CURRENT_DATE,INTERVAL days.day_offset DAY),'',200,200,sku.price_cent
FROM bf_resource_sku sku
CROSS JOIN (
    SELECT ones.n+tens.n day_offset
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 10 UNION ALL SELECT 20 UNION ALL SELECT 30) tens
    WHERE ones.n+tens.n<=30
) days
WHERE sku.sku_code IN ('SCENIC_WEAPON_ADULT_001','SCENIC_WUDANGZHAO_ADULT_001','SCENIC_MEIDAIZHAO_ADULT_001',
                       'SCENIC_SAIHANTALA_SERVICE_001','SCENIC_NANHAI_ADULT_001')
AND NOT EXISTS (
    SELECT 1 FROM bf_inventory inv
    WHERE inv.sku_id=sku.id AND inv.business_date=DATE_ADD(CURRENT_DATE,INTERVAL days.day_offset DAY)
      AND inv.time_slot=''
);
