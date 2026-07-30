-- 订单类型以后端订单明细的真实资源类型为准，修复早期由客户端传入造成的不一致。
UPDATE bf_order o
JOIN (
    SELECT order_id,
           CASE
               WHEN COUNT(DISTINCT resource_type) = 1 THEN MAX(resource_type)
               ELSE 'MIXED'
           END AS derived_order_type
    FROM bf_order_item
    GROUP BY order_id
) d ON d.order_id = o.id
SET o.order_type = d.derived_order_type
WHERE o.order_type <> d.derived_order_type;
