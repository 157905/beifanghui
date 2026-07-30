INSERT INTO bf_ticket_profile
(sku_id, audience_rule, usage_rule, refund_rule, entry_notice, valid_days,
 max_per_order, real_name_required, id_card_required)
SELECT id, '成人票，具体适用规则以运营配置为准', '仅限预约服务日期使用',
       '未核销前可按平台规则申请退款', '请携带有效身份证件入园', 1, 10, 1, 1
FROM bf_resource_sku WHERE sku_code='BFH_SCENIC_ADULT_001'
ON DUPLICATE KEY UPDATE
 audience_rule=VALUES(audience_rule), usage_rule=VALUES(usage_rule),
 refund_rule=VALUES(refund_rule), entry_notice=VALUES(entry_notice),
 valid_days=VALUES(valid_days), max_per_order=VALUES(max_per_order),
 real_name_required=VALUES(real_name_required), id_card_required=VALUES(id_card_required);
