package com.beifanghui.backend.content.api;

import com.beifanghui.backend.content.application.ContentManagementService;
import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ContentController {

    private final ContentManagementService service;

    public ContentController(ContentManagementService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/articles")
    public ApiResponse<PageResponse<ArticleResponse>> adminArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String articleType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.listArticles(keyword, articleType, status, page, pageSize, false),
                TraceIds.from(request));
    }

    @GetMapping("/api/v1/admin/articles/{articleId}")
    public ApiResponse<ArticleResponse> adminArticleDetail(@PathVariable long articleId, HttpServletRequest request) {
        return ApiResponse.success(service.articleDetail(articleId, false), TraceIds.from(request));
    }

    @PostMapping("/api/v1/admin/articles")
    public ApiResponse<ArticleResponse> createArticle(@RequestBody ArticleUpsertRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success("内容创建成功",
                service.createArticle(CurrentPrincipal.from(request), body), TraceIds.from(request));
    }

    @PutMapping("/api/v1/admin/articles/{articleId}")
    public ApiResponse<ArticleResponse> updateArticle(@PathVariable long articleId,
                                                       @RequestBody ArticleUpsertRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success("内容更新成功",
                service.updateArticle(CurrentPrincipal.from(request), articleId, body), TraceIds.from(request));
    }

    @PatchMapping("/api/v1/admin/articles/{articleId}/status")
    public ApiResponse<ArticleResponse> updateArticleStatus(@PathVariable long articleId,
                                                             @RequestBody ArticleStatusUpdateRequest body,
                                                             HttpServletRequest request) {
        return ApiResponse.success("发布状态更新成功",
                service.updateArticleStatus(CurrentPrincipal.from(request), articleId,
                        body == null ? null : body.status()), TraceIds.from(request));
    }

    @GetMapping("/api/v1/admin/banners")
    public ApiResponse<List<BannerResponse>> adminBanners(HttpServletRequest request) {
        return ApiResponse.success(service.listBanners(false), TraceIds.from(request));
    }

    @PostMapping("/api/v1/admin/banners")
    public ApiResponse<BannerResponse> createBanner(@RequestBody BannerUpsertRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.success("轮播创建成功",
                service.createBanner(CurrentPrincipal.from(request), body), TraceIds.from(request));
    }

    @PutMapping("/api/v1/admin/banners/{bannerId}")
    public ApiResponse<BannerResponse> updateBanner(@PathVariable long bannerId,
                                                     @RequestBody BannerUpsertRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.success("轮播更新成功",
                service.updateBanner(CurrentPrincipal.from(request), bannerId, body), TraceIds.from(request));
    }

    @PatchMapping("/api/v1/admin/banners/{bannerId}/enabled")
    public ApiResponse<BannerResponse> updateBannerEnabled(@PathVariable long bannerId,
                                                            @RequestBody BannerEnabledUpdateRequest body,
                                                            HttpServletRequest request) {
        return ApiResponse.success("轮播状态更新成功",
                service.updateBannerEnabled(CurrentPrincipal.from(request), bannerId,
                        body == null ? null : body.enabled()), TraceIds.from(request));
    }

    @GetMapping("/api/v1/app/content/articles")
    public ApiResponse<PageResponse<ArticleResponse>> publicArticles(
            @RequestParam(required = false) String articleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.listArticles(null, articleType, "PUBLISHED", page, pageSize, true),
                TraceIds.from(request));
    }

    @GetMapping("/api/v1/app/content/articles/{articleId}")
    public ApiResponse<ArticleResponse> publicArticleDetail(@PathVariable long articleId,
                                                             HttpServletRequest request) {
        return ApiResponse.success(service.articleDetail(articleId, true), TraceIds.from(request));
    }

    @GetMapping("/api/v1/app/content/banners")
    public ApiResponse<List<BannerResponse>> publicBanners(HttpServletRequest request) {
        return ApiResponse.success(service.listBanners(true), TraceIds.from(request));
    }
}
