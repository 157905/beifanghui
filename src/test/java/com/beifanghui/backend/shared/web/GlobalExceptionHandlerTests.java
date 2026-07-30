package com.beifanghui.backend.shared.web;

import com.beifanghui.backend.shared.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTests {

    @Test
    void shouldExplainInvalidResourceId() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "trace-id", Long.class, "resourceId", null, new NumberFormatException());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, "trace-001");

        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleTypeMismatch(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("SYSTEM_400_001", response.getBody().code());
    }
}
