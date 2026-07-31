package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.scenic.api.ScenicGuideResponse;
import com.beifanghui.backend.scenic.api.ScenicGuideUpsertRequest;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 景区地图、景点介绍、语音讲解和游玩攻略的管理与公开查询。 */
@Service
public class ScenicGuideService {
    private static final Set<String> CONTENT_TYPES = Set.of("MAP", "ATTRACTION", "AUDIO", "STRATEGY");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE");
    private final JdbcTemplate jdbcTemplate;

    public ScenicGuideService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<ScenicGuideResponse> listPublic(long scenicSpotId, String contentType) {
        ensureScenicSpot(scenicSpotId, true);
        if (StringUtils.hasText(contentType)) {
            return jdbcTemplate.query(selectSql() + " WHERE scenic_spot_id=? AND status='ACTIVE' AND content_type=? ORDER BY sort_order,id",
                    (rs, rowNum) -> toResponse(rs), scenicSpotId, normalizeContentType(contentType));
        }
        return jdbcTemplate.query(selectSql() + " WHERE scenic_spot_id=? AND status='ACTIVE' ORDER BY content_type,sort_order,id",
                (rs, rowNum) -> toResponse(rs), scenicSpotId);
    }

    @Transactional(readOnly = true)
    public ScenicGuideResponse detailPublic(long guideId) {
        List<ScenicGuideResponse> rows = jdbcTemplate.query(selectSql() + " WHERE id=? AND status='ACTIVE'",
                (rs, rowNum) -> toResponse(rs), guideId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区导览内容不存在或未发布");
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<ScenicGuideResponse> listAdmin(AuthenticatedPrincipal principal, long scenicSpotId) {
        ensureScenicSpot(scenicSpotId, false);
        return jdbcTemplate.query(selectSql() + " WHERE scenic_spot_id=? ORDER BY content_type,sort_order,id",
                (rs, rowNum) -> toResponse(rs), scenicSpotId);
    }

    @Transactional
    public ScenicGuideResponse create(AuthenticatedPrincipal principal, long scenicSpotId, ScenicGuideUpsertRequest request) {
        ensureScenicSpot(scenicSpotId, false);
        GuideValues values = validate(request);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_scenic_guide_content
                    (scenic_spot_id,content_type,title,summary,cover_url,content_url,content_text,duration_seconds,sort_order,status)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, scenicSpotId);
            statement.setString(2, values.contentType());
            statement.setString(3, values.title());
            statement.setString(4, values.summary());
            statement.setString(5, values.coverUrl());
            statement.setString(6, values.contentUrl());
            statement.setString(7, values.contentText());
            if (values.durationSeconds() == null) statement.setNull(8, java.sql.Types.INTEGER); else statement.setInt(8, values.durationSeconds());
            statement.setInt(9, values.sortOrder());
            statement.setString(10, values.status());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "景区导览内容创建失败");
        long guideId = key.longValue();
        audit(ensureOperator(principal), "SCENIC_GUIDE_CREATE", guideId, scenicSpotId, values.contentType(), values.status());
        return find(guideId);
    }

    @Transactional
    public ScenicGuideResponse update(AuthenticatedPrincipal principal, long guideId, ScenicGuideUpsertRequest request) {
        GuideValues values = validate(request);
        GuideLocked current = lockGuide(guideId);
        int updated = jdbcTemplate.update("""
                UPDATE bf_scenic_guide_content
                SET content_type=?,title=?,summary=?,cover_url=?,content_url=?,content_text=?,duration_seconds=?,sort_order=?,status=?
                WHERE id=?
                """, values.contentType(), values.title(), values.summary(), values.coverUrl(), values.contentUrl(),
                values.contentText(), values.durationSeconds(), values.sortOrder(), values.status(), guideId);
        if (updated == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区导览内容不存在");
        audit(ensureOperator(principal), "SCENIC_GUIDE_UPDATE", guideId, current.scenicSpotId(), values.contentType(), values.status());
        return find(guideId);
    }

    private String selectSql() {
        return """
                SELECT id,scenic_spot_id,content_type,title,summary,cover_url,content_url,content_text,
                       duration_seconds,sort_order,status,updated_at
                FROM bf_scenic_guide_content
                """;
    }

    private ScenicGuideResponse find(long guideId) {
        List<ScenicGuideResponse> rows = jdbcTemplate.query(selectSql() + " WHERE id=?", (rs, rowNum) -> toResponse(rs), guideId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区导览内容不存在");
        return rows.get(0);
    }

    private ScenicGuideResponse toResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ScenicGuideResponse(rs.getLong("id"), rs.getLong("scenic_spot_id"), rs.getString("content_type"),
                rs.getString("title"), rs.getString("summary"), rs.getString("cover_url"), rs.getString("content_url"),
                rs.getString("content_text"), rs.getObject("duration_seconds", Integer.class), rs.getInt("sort_order"),
                rs.getString("status"), rs.getObject("updated_at", LocalDateTime.class));
    }

    private void ensureScenicSpot(long scenicSpotId, boolean activeOnly) {
        String sql = "SELECT COUNT(*) FROM bf_resource WHERE id=? AND resource_type='SCENIC_TICKET'" + (activeOnly ? " AND status='ACTIVE'" : "");
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, scenicSpotId);
        if (count == null || count == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区不存在或未上架");
    }

    private GuideLocked lockGuide(long guideId) {
        List<GuideLocked> rows = jdbcTemplate.query("SELECT scenic_spot_id FROM bf_scenic_guide_content WHERE id=? FOR UPDATE",
                (rs, rowNum) -> new GuideLocked(rs.getLong("scenic_spot_id")), guideId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区导览内容不存在");
        return rows.get(0);
    }

    private GuideValues validate(ScenicGuideUpsertRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求体不能为空");
        String type = normalizeContentType(request.contentType());
        String title = required(request.title(), "title", 200);
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        if (!STATUSES.contains(status)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status 仅支持 DRAFT、ACTIVE、INACTIVE");
        if (request.durationSeconds() != null && request.durationSeconds() < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "durationSeconds 不能小于 0");
        }
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        return new GuideValues(type, title, optional(request.summary(), 500), optional(request.coverUrl(), 512),
                optional(request.contentUrl(), 512), optional(request.contentText(), 65535), request.durationSeconds(), sortOrder, status);
    }

    private String normalizeContentType(String value) {
        String type = required(value, "contentType", 32).toUpperCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(type)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "contentType 仅支持 MAP、ATTRACTION、AUDIO、STRATEGY");
        return type;
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, field + " 不能为空");
        String result = value.trim();
        if (result.length() > maxLength) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, field + " 长度不能超过 " + maxLength);
        return result;
    }

    private String optional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim();
        if (result.length() > maxLength) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "字段长度不能超过 " + maxLength);
        return result;
    }

    private long ensureOperator(AuthenticatedPrincipal principal) {
        String openid = principal.databaseOpenId();
        jdbcTemplate.update("""
                INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),status='ACTIVE'
                """, openid, principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class, openid);
    }

    private void audit(long operatorId, String action, long guideId, long scenicSpotId, String contentType, String status) {
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,?,?,?,JSON_OBJECT('scenicSpotId',?,'contentType',?,'status',?))
                """, operatorId, action, "SCENIC_GUIDE", String.valueOf(guideId), scenicSpotId, contentType, status);
    }

    private record GuideLocked(long scenicSpotId) { }
    private record GuideValues(String contentType, String title, String summary, String coverUrl, String contentUrl,
                               String contentText, Integer durationSeconds, int sortOrder, String status) { }
}
