package com.beifanghui.backend.shared.audit;

import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditLogQueryService {
    private final JdbcTemplate jdbcTemplate;
    public AuditLogQueryService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate=jdbcTemplate; }

    public PageResponse<AuditLogResponse> list(String action,String targetType,int page,int pageSize) {
        if(page<1||pageSize<1||pageSize>100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"page最小为1，pageSize范围为1—100");
        }
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(StringUtils.hasText(action)){where.append(" AND a.action=?");args.add(action.trim());}
        if(StringUtils.hasText(targetType)){where.append(" AND a.target_type=?");args.add(targetType.trim());}
        long total=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_audit_log a"+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(pageSize);pageArgs.add((page-1)*pageSize);
        List<AuditLogResponse> items=jdbcTemplate.query("""
                SELECT a.id,a.operator_id,u.nickname,a.action,a.target_type,a.target_id,
                       CAST(a.detail AS CHAR) detail,a.created_at
                FROM bf_audit_log a LEFT JOIN bf_user u ON u.id=a.operator_id
                """+where+" ORDER BY a.id DESC LIMIT ? OFFSET ?",(rs,n)->new AuditLogResponse(
                rs.getLong("id"),rs.getObject("operator_id",Long.class),rs.getString("nickname"),
                rs.getString("action"),rs.getString("target_type"),rs.getString("target_id"),
                rs.getString("detail"),rs.getObject("created_at",LocalDateTime.class)),pageArgs.toArray());
        int pages=total==0?0:(int)((total+pageSize-1)/pageSize);
        return new PageResponse<>(items,page,pageSize,total,pages);
    }
}
