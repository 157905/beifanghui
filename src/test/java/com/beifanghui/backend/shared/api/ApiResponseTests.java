package com.beifanghui.backend.shared.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiResponseTests {

    @Test
    void shouldBuildUnifiedSuccessResponse() {
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                Map.of("status", "UP"), "trace-001");

        assertEquals("SUCCESS", response.code());
        assertEquals("UP", response.data().get("status"));
        assertEquals("trace-001", response.traceId());
    }
}
