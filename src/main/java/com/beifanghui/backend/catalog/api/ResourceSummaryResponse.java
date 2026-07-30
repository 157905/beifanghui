package com.beifanghui.backend.catalog.api;

public record ResourceSummaryResponse(Long id, Long siteId, String resourceType, String categoryCode,
                                      String name, String description, String coverUrl) {}
