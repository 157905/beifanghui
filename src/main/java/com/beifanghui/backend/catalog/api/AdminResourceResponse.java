package com.beifanghui.backend.catalog.api;

import java.time.LocalDateTime;

public record AdminResourceResponse(long id, String resourceType, String name, String status,
                                    LocalDateTime updatedAt) {}
