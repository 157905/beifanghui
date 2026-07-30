package com.beifanghui.backend.shared.web;

import jakarta.servlet.http.HttpServletRequest;

public final class TraceIds {

    private TraceIds() {
    }

    public static String from(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
