package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.order.extension.OrderBusinessContext;
import com.beifanghui.backend.order.extension.OrderBusinessExtension;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import com.beifanghui.backend.shared.security.SensitiveDataCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScenicTicketOrderExtension implements OrderBusinessExtension {
    private final JdbcTemplate jdbcTemplate;
    private final SensitiveDataCipher cipher;

    public ScenicTicketOrderExtension(JdbcTemplate jdbcTemplate, SensitiveDataCipher cipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
    }

    @Override
    public String resourceType() { return "SCENIC_TICKET"; }

    @Override
    public void validate(OrderBusinessContext context) {
        if (context.serviceDate() == null || context.serviceDate().isBefore(LocalDate.now())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "游玩日期不能早于今天");
        }
        TicketRule rule = ticketRule(context.skuId());
        List<Visitor> visitors = visitors(context.businessData());
        if (rule.maxPerOrder() != null && context.quantity() > rule.maxPerOrder()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "该票种每单最多购买" + rule.maxPerOrder() + "张");
        }
        if (rule.realNameRequired() && visitors.size() != context.quantity()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "实名票的游客数量必须与购票数量一致");
        }
        for (Visitor visitor : visitors) validateVisitor(visitor, rule.idCardRequired());
    }

    @Override
    public void save(long orderId, long orderItemId, OrderBusinessContext context) {
        for (Visitor visitor : visitors(context.businessData())) {
            jdbcTemplate.update("""
                    INSERT INTO bf_order_person
                    (order_item_id,person_type,name_cipher,mobile_cipher,id_type,id_no_cipher,id_no_hash)
                    VALUES (?,'VISITOR',?,?,?,?,?)
                    """, orderItemId, cipher.encrypt(visitor.name()), cipher.encrypt(visitor.mobile()),
                    visitor.idType(), cipher.encrypt(visitor.idNo()),
                    visitor.idNo() == null ? null : cipher.searchHash(visitor.idNo()));
        }
    }

    private TicketRule ticketRule(long skuId) {
        List<TicketRule> rows = jdbcTemplate.query("""
                SELECT real_name_required,id_card_required,max_per_order
                FROM bf_ticket_profile WHERE sku_id=?
                """, (rs,n) -> new TicketRule(rs.getBoolean("real_name_required"),
                rs.getBoolean("id_card_required"),rs.getObject("max_per_order",Integer.class)),skuId);
        return rows.isEmpty() ? new TicketRule(false,false,null) : rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Visitor> visitors(Map<String,Object> businessData) {
        if (businessData == null || businessData.get("visitors") == null) return List.of();
        Object source = businessData.get("visitors");
        if (!(source instanceof List<?> list)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"businessData.visitors必须是数组");
        }
        List<Visitor> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?,?> map)) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"游客信息格式不正确");
            }
            String idType = text(map.get("idType"));
            String idNo = text(map.get("idNo"));
            result.add(new Visitor(text(map.get("name")),text(map.get("mobile")), idType,
                    "ID_CARD".equals(idType) && idNo != null ? idNo.toUpperCase(java.util.Locale.ROOT) : idNo));
        }
        return result;
    }

    private void validateVisitor(Visitor visitor, boolean idCardRequired) {
        if (!StringUtils.hasText(visitor.name()) || visitor.name().length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"游客姓名不能为空且最长64字符");
        }
        if (idCardRequired && (!"ID_CARD".equals(visitor.idType()) || !StringUtils.hasText(visitor.idNo()))) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"该票种必须提供身份证信息");
        }
        if (StringUtils.hasText(visitor.idNo()) && (visitor.idNo().length() < 6 || visitor.idNo().length() > 30)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"证件号码长度应为6—30字符");
        }
        if (StringUtils.hasText(visitor.mobile()) && visitor.mobile().length() > 32) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"手机号最长32字符");
        }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private record TicketRule(boolean realNameRequired,boolean idCardRequired,Integer maxPerOrder) {}
    private record Visitor(String name,String mobile,String idType,String idNo) {}
}
