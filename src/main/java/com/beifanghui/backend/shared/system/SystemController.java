package com.beifanghui.backend.shared.system;

import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/app/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health(HttpServletRequest request) {
        Map<String, Object> data = Map.of(
                "status", "UP",
                "application", applicationName,
                "time", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        return ApiResponse.success(data, TraceIds.from(request));
    }
}
