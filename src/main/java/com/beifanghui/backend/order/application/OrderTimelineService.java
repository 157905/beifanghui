package com.beifanghui.backend.order.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.api.OrderTimelineResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderTimelineService {
    private final JdbcTemplate jdbcTemplate;
    public OrderTimelineService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional(readOnly = true)
    public OrderTimelineResponse timeline(AuthenticatedPrincipal principal, long orderId) {
        List<OrderHead> heads = jdbcTemplate.query("""
                SELECT o.order_no,o.status FROM bf_order o JOIN bf_user u ON u.id=o.user_id
                WHERE o.id=? AND u.wechat_openid=?
                """, (rs,n) -> new OrderHead(rs.getString("order_no"),rs.getString("status")),
                orderId,principal.databaseOpenId());
        if (heads.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND,"订单不存在或无权访问");
        List<OrderTimelineResponse.Event> events = jdbcTemplate.query("""
                SELECT event_type,event_status,amount_cent,reference_no,note,event_time FROM (
                  SELECT 'ORDER_CREATED' event_type,'PENDING_PAYMENT' event_status,NULL amount_cent,
                         o.order_no reference_no,'订单创建' note,o.created_at event_time,0 sort_no
                  FROM bf_order o WHERE o.id=?
                  UNION ALL
                  SELECT 'ORDER_STATUS',l.to_status,NULL,NULL,l.reason,l.created_at,1
                  FROM bf_order_status_log l WHERE l.order_id=?
                  UNION ALL
                  SELECT 'PAYMENT',p.status,p.amount_cent,p.payment_no,p.channel,COALESCE(p.paid_at,p.created_at),2
                  FROM bf_payment p WHERE p.order_id=?
                  UNION ALL
                  SELECT 'REFUND',log.to_status,r.amount_cent,r.refund_no,log.note,log.created_at,3
                  FROM bf_refund_status_log log JOIN bf_refund r ON r.id=log.refund_id WHERE r.order_id=?
                  UNION ALL
                  SELECT 'REFUND',r.status,r.amount_cent,r.refund_no,r.reason,COALESCE(r.refunded_at,r.created_at),3
                  FROM bf_refund r WHERE r.order_id=?
                    AND NOT EXISTS (SELECT 1 FROM bf_refund_status_log log WHERE log.refund_id=r.id)
                  UNION ALL
                  SELECT 'VERIFICATION',v.status,NULL,CAST(v.id AS CHAR),i.resource_name,
                         COALESCE(v.verified_at,v.created_at),4
                  FROM bf_verification v JOIN bf_order_item i ON i.id=v.order_item_id WHERE i.order_id=?
                ) e ORDER BY event_time,sort_no
                """, (rs,n) -> new OrderTimelineResponse.Event(rs.getString("event_type"),
                rs.getString("event_status"),rs.getObject("amount_cent",Long.class),
                rs.getString("reference_no"),rs.getString("note"),
                rs.getObject("event_time",LocalDateTime.class)), orderId,orderId,orderId,orderId,orderId,orderId);
        OrderHead head=heads.get(0);
        return new OrderTimelineResponse(orderId,head.orderNo(),head.status(),events);
    }

    private record OrderHead(String orderNo,String status) {}
}
