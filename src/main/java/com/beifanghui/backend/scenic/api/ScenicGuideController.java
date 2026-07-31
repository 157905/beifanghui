package com.beifanghui.backend.scenic.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.scenic.application.ScenicGuideService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScenicGuideController {
    private final ScenicGuideService service;

    public ScenicGuideController(ScenicGuideService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/app/scenic-spots/{scenicSpotId}/guides")
    public ApiResponse<List<ScenicGuideResponse>> listPublic(@PathVariable long scenicSpotId,
                                                               @RequestParam(required = false) String contentType,
                                                               HttpServletRequest request) {
        return ApiResponse.success(service.listPublic(scenicSpotId, contentType), TraceIds.from(request));
    }

    @GetMapping("/api/v1/app/scenic-guides/{guideId}")
    public ApiResponse<ScenicGuideResponse> detailPublic(@PathVariable long guideId, HttpServletRequest request) {
        return ApiResponse.success(service.detailPublic(guideId), TraceIds.from(request));
    }

    @GetMapping("/api/v1/admin/scenic-spots/{scenicSpotId}/guides")
    public ApiResponse<List<ScenicGuideResponse>> listAdmin(@PathVariable long scenicSpotId,
                                                              HttpServletRequest request) {
        return ApiResponse.success(service.listAdmin(CurrentPrincipal.from(request), scenicSpotId), TraceIds.from(request));
    }

    @PostMapping("/api/v1/admin/scenic-spots/{scenicSpotId}/guides")
    public ApiResponse<ScenicGuideResponse> create(@PathVariable long scenicSpotId,
                                                     @RequestBody ScenicGuideUpsertRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.success("景区导览内容创建成功", service.create(CurrentPrincipal.from(request), scenicSpotId, body),
                TraceIds.from(request));
    }

    @PutMapping("/api/v1/admin/scenic-guides/{guideId}")
    public ApiResponse<ScenicGuideResponse> update(@PathVariable long guideId,
                                                     @RequestBody ScenicGuideUpsertRequest body,
                                                     HttpServletRequest request) {
        return ApiResponse.success("景区导览内容更新成功", service.update(CurrentPrincipal.from(request), guideId, body),
                TraceIds.from(request));
    }
}
