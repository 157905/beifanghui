package com.beifanghui.backend.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bf_resource_sku")
public class ResourceSkuEntity {
    @Id private Long id;
    @Column(name = "resource_id", nullable = false) private Long resourceId;
    @Column(name = "sku_code", nullable = false) private String skuCode;
    @Column(nullable = false) private String name;
    @Column(name = "price_cent", nullable = false) private Long priceCent;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "json") private String attributes;

    protected ResourceSkuEntity() {}
    public Long getId() { return id; }
    public Long getResourceId() { return resourceId; }
    public String getSkuCode() { return skuCode; }
    public String getName() { return name; }
    public Long getPriceCent() { return priceCent; }
    public String getStatus() { return status; }
    public String getAttributes() { return attributes; }
}
