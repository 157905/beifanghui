package com.beifanghui.backend.content.application;

import com.beifanghui.backend.content.api.*;
import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.shared.api.PageResponse;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ContentManagementService {

    private static final Set<String> ARTICLE_TYPES = Set.of("NEWS", "NOTICE", "ACTIVITY", "TRAVEL_GUIDE");
    private static final Set<String> ARTICLE_STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");
    private static final Set<String> TARGET_TYPES = Set.of("NONE", "PATH", "RESOURCE", "ARTICLE", "URL");
    private final JdbcTemplate jdbcTemplate;

    public ContentManagementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleResponse> listArticles(String keyword, String articleType, String status,
                                                       int page, int pageSize, boolean publicOnly) {
        validatePage(page, pageSize);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (title LIKE ? OR summary LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
        }
        if (StringUtils.hasText(articleType)) {
            where.append(" AND article_type=?");
            args.add(normalizeArticleType(articleType));
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status=?");
            args.add(normalizeArticleStatus(status));
        }
        if (publicOnly) {
            where.append(" AND status='PUBLISHED' AND published_at IS NOT NULL AND published_at<=NOW()");
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_article" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<ArticleResponse> items = jdbcTemplate.query(articleSelect() + where
                        + " ORDER BY pinned DESC,COALESCE(published_at,created_at) DESC,id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ArticleResponse(
                        rs.getLong("id"), rs.getString("article_type"), rs.getString("title"),
                        rs.getString("summary"), rs.getString("cover_url"), rs.getString("content"),
                        rs.getString("status"), rs.getBoolean("pinned"),
                        rs.getObject("published_at", LocalDateTime.class), rs.getObject("created_by", Long.class),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class)), pageArgs.toArray());
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public ArticleResponse articleDetail(long articleId, boolean publicOnly) {
        String condition = publicOnly ? " AND status='PUBLISHED' AND published_at<=NOW()" : "";
        List<ArticleResponse> rows = jdbcTemplate.query(articleSelect() + " WHERE id=?" + condition,
                (rs, rowNum) -> new ArticleResponse(
                        rs.getLong("id"), rs.getString("article_type"), rs.getString("title"),
                        rs.getString("summary"), rs.getString("cover_url"), rs.getString("content"),
                        rs.getString("status"), rs.getBoolean("pinned"),
                        rs.getObject("published_at", LocalDateTime.class), rs.getObject("created_by", Long.class),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class)), articleId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "内容不存在");
        return rows.get(0);
    }

    @Transactional
    public ArticleResponse createArticle(AuthenticatedPrincipal principal, ArticleUpsertRequest request) {
        ArticleValues values = validateArticle(request);
        long operatorId = ensureOperator(principal);
        LocalDateTime publishedAt = "PUBLISHED".equals(values.status()) ? LocalDateTime.now() : null;
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_article(article_type,title,summary,cover_url,content,status,pinned,published_at,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, values.articleType());
            statement.setString(2, values.title());
            statement.setString(3, values.summary());
            statement.setString(4, values.coverUrl());
            statement.setString(5, values.content());
            statement.setString(6, values.status());
            statement.setBoolean(7, values.pinned());
            statement.setTimestamp(8, publishedAt == null ? null : Timestamp.valueOf(publishedAt));
            statement.setLong(9, operatorId);
            return statement;
        }, holder);
        long id = holder.getKey().longValue();
        audit(operatorId, "ARTICLE_CREATE", "ARTICLE", id, values.status());
        return articleDetail(id, false);
    }

    @Transactional
    public ArticleResponse updateArticle(AuthenticatedPrincipal principal, long articleId,
                                          ArticleUpsertRequest request) {
        requireArticle(articleId);
        ArticleValues values = validateArticle(request);
        jdbcTemplate.update("""
                UPDATE bf_article SET article_type=?,title=?,summary=?,cover_url=?,content=?,status=?,pinned=?,
                  published_at=CASE WHEN ?='PUBLISHED' THEN COALESCE(published_at,NOW()) ELSE published_at END
                WHERE id=?
                """, values.articleType(), values.title(), values.summary(), values.coverUrl(), values.content(),
                values.status(), values.pinned(), values.status(), articleId);
        audit(ensureOperator(principal), "ARTICLE_UPDATE", "ARTICLE", articleId, values.status());
        return articleDetail(articleId, false);
    }

    @Transactional
    public ArticleResponse updateArticleStatus(AuthenticatedPrincipal principal, long articleId, String status) {
        requireArticle(articleId);
        String value = normalizeArticleStatus(status);
        jdbcTemplate.update("""
                UPDATE bf_article SET status=?,published_at=CASE WHEN ?='PUBLISHED'
                  THEN COALESCE(published_at,NOW()) ELSE published_at END WHERE id=?
                """, value, value, articleId);
        audit(ensureOperator(principal), "ARTICLE_STATUS_UPDATE", "ARTICLE", articleId, value);
        return articleDetail(articleId, false);
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> listBanners(boolean publicOnly) {
        String where = publicOnly
                ? " WHERE enabled=1 AND (start_at IS NULL OR start_at<=NOW()) AND (end_at IS NULL OR end_at>=NOW())"
                : "";
        return jdbcTemplate.query(bannerSelect() + where + " ORDER BY sort_order,id DESC",
                (rs, rowNum) -> toBanner(rs));
    }

    @Transactional
    public BannerResponse createBanner(AuthenticatedPrincipal principal, BannerUpsertRequest request) {
        BannerValues values = validateBanner(request);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_banner(title,image_url,target_type,target_value,start_at,end_at,sort_order,enabled)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            setBannerValues(statement, values);
            return statement;
        }, holder);
        long id = holder.getKey().longValue();
        audit(ensureOperator(principal), "BANNER_CREATE", "BANNER", id, String.valueOf(values.enabled()));
        return findBanner(id);
    }

    @Transactional
    public BannerResponse updateBanner(AuthenticatedPrincipal principal, long bannerId, BannerUpsertRequest request) {
        requireBanner(bannerId);
        BannerValues values = validateBanner(request);
        jdbcTemplate.update("""
                UPDATE bf_banner SET title=?,image_url=?,target_type=?,target_value=?,start_at=?,end_at=?,sort_order=?,enabled=?
                WHERE id=?
                """, values.title(), values.imageUrl(), values.targetType(), values.targetValue(), values.startAt(),
                values.endAt(), values.sortOrder(), values.enabled(), bannerId);
        audit(ensureOperator(principal), "BANNER_UPDATE", "BANNER", bannerId, String.valueOf(values.enabled()));
        return findBanner(bannerId);
    }

    @Transactional
    public BannerResponse updateBannerEnabled(AuthenticatedPrincipal principal, long bannerId, Boolean enabled) {
        requireBanner(bannerId);
        if (enabled == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "enabled不能为空");
        jdbcTemplate.update("UPDATE bf_banner SET enabled=? WHERE id=?", enabled, bannerId);
        audit(ensureOperator(principal), "BANNER_STATUS_UPDATE", "BANNER", bannerId, String.valueOf(enabled));
        return findBanner(bannerId);
    }

    private String articleSelect() {
        return """
                SELECT id,article_type,title,summary,cover_url,content,status,pinned,published_at,
                       created_by,created_at,updated_at FROM bf_article
                """;
    }

    private String bannerSelect() {
        return """
                SELECT id,title,image_url,target_type,target_value,start_at,end_at,sort_order,enabled,created_at,updated_at
                FROM bf_banner
                """;
    }

    private BannerResponse findBanner(long id) {
        List<BannerResponse> rows = jdbcTemplate.query(bannerSelect() + " WHERE id=?",
                (rs, rowNum) -> toBanner(rs), id);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "轮播不存在");
        return rows.get(0);
    }

    private BannerResponse toBanner(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BannerResponse(rs.getLong("id"), rs.getString("title"), rs.getString("image_url"),
                rs.getString("target_type"), rs.getString("target_value"),
                rs.getObject("start_at", LocalDateTime.class), rs.getObject("end_at", LocalDateTime.class),
                rs.getInt("sort_order"), rs.getBoolean("enabled"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private ArticleValues validateArticle(ArticleUpsertRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求内容不能为空");
        return new ArticleValues(normalizeArticleType(request.articleType()), required(request.title(), "title", 200),
                optional(request.summary(), 500), optional(request.coverUrl(), 512),
                required(request.content(), "content", 200000), normalizeArticleStatus(request.status()),
                Boolean.TRUE.equals(request.pinned()));
    }

    private BannerValues validateBanner(BannerUpsertRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求内容不能为空");
        String targetType = required(request.targetType(), "targetType", 32).toUpperCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "targetType仅支持NONE、PATH、RESOURCE、ARTICLE、URL");
        }
        if (request.startAt() != null && request.endAt() != null && request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "结束时间不能早于开始时间");
        }
        String targetValue = optional(request.targetValue(), 255);
        if (!"NONE".equals(targetType) && !StringUtils.hasText(targetValue)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "存在跳转目标时targetValue不能为空");
        }
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        if (sortOrder < 0 || sortOrder > 9999) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "sortOrder范围为0—9999");
        }
        return new BannerValues(required(request.title(), "title", 100),
                required(request.imageUrl(), "imageUrl", 512), targetType, targetValue,
                request.startAt(), request.endAt(), sortOrder, !Boolean.FALSE.equals(request.enabled()));
    }

    private String normalizeArticleType(String value) {
        String result = required(value, "articleType", 32).toUpperCase(Locale.ROOT);
        if (!ARTICLE_TYPES.contains(result)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "articleType仅支持NEWS、NOTICE、ACTIVITY、TRAVEL_GUIDE");
        }
        return result;
    }

    private String normalizeArticleStatus(String value) {
        String result = required(value, "status", 20).toUpperCase(Locale.ROOT);
        if (!ARTICLE_STATUSES.contains(result)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status仅支持DRAFT、PUBLISHED、OFFLINE");
        }
        return result;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page最小为1，pageSize范围为1—100");
        }
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, field + "不能为空");
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, field + "最长" + maxLength + "字符");
        }
        return result;
    }

    private String optional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim();
        if (result.length() > maxLength) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "字段最长" + maxLength + "字符");
        }
        return result;
    }

    private void requireArticle(long id) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_article WHERE id=?", Integer.class, id) == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "内容不存在");
        }
    }

    private void requireBanner(long id) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_banner WHERE id=?", Integer.class, id) == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "轮播不存在");
        }
    }

    private long ensureOperator(AuthenticatedPrincipal principal) {
        String openId = principal.databaseOpenId();
        jdbcTemplate.update("""
                INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname)
                """, openId, principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class, openId);
    }

    private void audit(long operatorId, String action, String targetType, long targetId, String status) {
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,?,?,?,JSON_OBJECT('status',?))
                """, operatorId, action, targetType, String.valueOf(targetId), status);
    }

    private void setBannerValues(PreparedStatement statement, BannerValues values) throws java.sql.SQLException {
        statement.setString(1, values.title());
        statement.setString(2, values.imageUrl());
        statement.setString(3, values.targetType());
        statement.setString(4, values.targetValue());
        statement.setTimestamp(5, values.startAt() == null ? null : Timestamp.valueOf(values.startAt()));
        statement.setTimestamp(6, values.endAt() == null ? null : Timestamp.valueOf(values.endAt()));
        statement.setInt(7, values.sortOrder());
        statement.setBoolean(8, values.enabled());
    }

    private record ArticleValues(String articleType, String title, String summary, String coverUrl,
                                 String content, String status, boolean pinned) {
    }

    private record BannerValues(String title, String imageUrl, String targetType, String targetValue,
                                LocalDateTime startAt, LocalDateTime endAt, int sortOrder, boolean enabled) {
    }
}
