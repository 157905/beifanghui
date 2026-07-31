-- 景区运营统计与核销记录按日期查询的辅助索引。
CREATE INDEX idx_order_item_service_date ON bf_order_item (service_date);
CREATE INDEX idx_verification_status_verified_at ON bf_verification (status, verified_at);
