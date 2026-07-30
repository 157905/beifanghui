package com.beifanghui.backend.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "bf_inventory")
public class InventoryEntity {
    @Id private Long id;
    @Column(name = "sku_id", nullable = false) private Long skuId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(name = "time_slot", nullable = false) private String timeSlot;
    @Column(name = "total_quantity", nullable = false) private Integer totalQuantity;
    @Column(name = "available_quantity", nullable = false) private Integer availableQuantity;
    @Column(name = "price_cent", nullable = false) private Long priceCent;
    @Column(nullable = false) private Integer version;

    protected InventoryEntity() {}
    public Long getId() { return id; }
    public Long getSkuId() { return skuId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getTimeSlot() { return timeSlot; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public Long getPriceCent() { return priceCent; }
    public Integer getVersion() { return version; }
}
