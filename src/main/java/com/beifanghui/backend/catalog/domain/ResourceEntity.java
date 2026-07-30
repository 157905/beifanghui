package com.beifanghui.backend.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bf_resource")
public class ResourceEntity {
    @Id private Long id;
    @Column(name = "site_id") private Long siteId;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(name = "category_code") private String categoryCode;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "cover_url") private String coverUrl;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "json") private String attributes;

    protected ResourceEntity() {}
    public Long getId() { return id; }
    public Long getSiteId() { return siteId; }
    public String getResourceType() { return resourceType; }
    public String getCategoryCode() { return categoryCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCoverUrl() { return coverUrl; }
    public String getStatus() { return status; }
    public String getAttributes() { return attributes; }
}
