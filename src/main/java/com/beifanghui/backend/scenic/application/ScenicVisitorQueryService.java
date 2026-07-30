package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.scenic.api.ScenicVisitorResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import com.beifanghui.backend.shared.security.SensitiveDataCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScenicVisitorQueryService {
    private final JdbcTemplate jdbcTemplate;
    private final SensitiveDataCipher cipher;
    public ScenicVisitorQueryService(JdbcTemplate jdbcTemplate,SensitiveDataCipher cipher){
        this.jdbcTemplate=jdbcTemplate;this.cipher=cipher;
    }

    public List<ScenicVisitorResponse> list(AuthenticatedPrincipal principal,long orderId){
        Integer owned=jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bf_order o JOIN bf_user u ON u.id=o.user_id
                WHERE o.id=? AND u.wechat_openid=?
                """,Integer.class,orderId,principal.databaseOpenId());
        if(owned==null||owned==0) throw new BusinessException(CommonErrorCode.NOT_FOUND,"订单不存在或无权访问");
        return jdbcTemplate.query("""
                SELECT p.id,p.order_item_id,p.person_type,p.name_cipher,p.mobile_cipher,p.id_type,p.id_no_cipher
                FROM bf_order_person p JOIN bf_order_item i ON i.id=p.order_item_id
                WHERE i.order_id=? ORDER BY p.id
                """,(rs,n)->new ScenicVisitorResponse(rs.getLong("id"),rs.getLong("order_item_id"),
                rs.getString("person_type"),maskName(cipher.decrypt(rs.getBytes("name_cipher"))),
                maskMobile(cipher.decrypt(rs.getBytes("mobile_cipher"))),rs.getString("id_type"),
                maskIdNo(cipher.decrypt(rs.getBytes("id_no_cipher")))),orderId);
    }

    private String maskName(String value){
        if(value==null||value.isEmpty())return value;
        return value.length()==1?"*":value.substring(0,1)+"*".repeat(value.length()-1);
    }
    private String maskMobile(String value){
        if(value==null||value.length()<7)return value==null?null:"****";
        return value.substring(0,3)+"****"+value.substring(value.length()-4);
    }
    private String maskIdNo(String value){
        if(value==null||value.length()<8)return value==null?null:"********";
        return value.substring(0,3)+"***********"+value.substring(value.length()-4);
    }
}
