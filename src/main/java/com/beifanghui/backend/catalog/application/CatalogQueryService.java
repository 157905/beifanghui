package com.beifanghui.backend.catalog.application;

import com.beifanghui.backend.catalog.api.AvailabilityResponse;
import com.beifanghui.backend.catalog.api.ResourceDetailResponse;
import com.beifanghui.backend.catalog.api.ResourceSummaryResponse;
import com.beifanghui.backend.catalog.domain.InventoryEntity;
import com.beifanghui.backend.catalog.domain.ResourceEntity;
import com.beifanghui.backend.catalog.domain.ResourceSkuEntity;
import com.beifanghui.backend.catalog.infrastructure.InventoryJpaRepository;
import com.beifanghui.backend.catalog.infrastructure.ResourceJpaRepository;
import com.beifanghui.backend.catalog.infrastructure.ResourceSkuJpaRepository;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {
    private static final String ACTIVE = "ACTIVE";
    private final ResourceJpaRepository resourceRepository;
    private final ResourceSkuJpaRepository skuRepository;
    private final InventoryJpaRepository inventoryRepository;

    public CatalogQueryService(ResourceJpaRepository resourceRepository,
                               ResourceSkuJpaRepository skuRepository,
                               InventoryJpaRepository inventoryRepository) {
        this.resourceRepository = resourceRepository;
        this.skuRepository = skuRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public PageResponse<ResourceSummaryResponse> list(String type, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page 从 1 开始，pageSize 范围为 1—100");
        }
        PageRequest pageable = PageRequest.of(page - 1, pageSize, Sort.by("id").descending());
        Page<ResourceEntity> result = StringUtils.hasText(type)
                ? resourceRepository.findByStatusAndResourceType(ACTIVE, type.trim().toUpperCase(Locale.ROOT), pageable)
                : resourceRepository.findByStatus(ACTIVE, pageable);
        List<ResourceSummaryResponse> items = result.getContent().stream().map(this::toSummary).toList();
        return PageResponse.from(result, items);
    }

    public ResourceDetailResponse detail(Long resourceId) {
        ResourceEntity resource = resourceRepository.findByIdAndStatus(resourceId, ACTIVE)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "资源不存在或未上架"));
        List<ResourceDetailResponse.SkuResponse> skus = skuRepository
                .findByResourceIdAndStatusOrderByPriceCentAsc(resourceId, ACTIVE)
                .stream().map(this::toSku).toList();
        return new ResourceDetailResponse(resource.getId(), resource.getSiteId(), resource.getResourceType(),
                resource.getCategoryCode(), resource.getName(), resource.getDescription(), resource.getCoverUrl(),
                resource.getAttributes(), skus);
    }

    public AvailabilityResponse availability(Long skuId, LocalDate date, String timeSlot) {
        ResourceSkuEntity sku = skuRepository.findByIdAndStatus(skuId, ACTIVE)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "SKU 不存在或未启用"));
        String slot = timeSlot == null ? "" : timeSlot.trim();
        InventoryEntity inventory = inventoryRepository
                .findBySkuIdAndBusinessDateAndTimeSlot(skuId, date, slot).orElse(null);
        if (inventory == null) {
            return new AvailabilityResponse(skuId, date, slot, false, 0, 0,
                    sku.getPriceCent(), "CNY", false);
        }
        return new AvailabilityResponse(skuId, date, slot, inventory.getAvailableQuantity() > 0,
                inventory.getTotalQuantity(), inventory.getAvailableQuantity(), inventory.getPriceCent(),
                "CNY", true);
    }

    private ResourceSummaryResponse toSummary(ResourceEntity resource) {
        return new ResourceSummaryResponse(resource.getId(), resource.getSiteId(), resource.getResourceType(),
                resource.getCategoryCode(), resource.getName(), resource.getDescription(), resource.getCoverUrl());
    }

    private ResourceDetailResponse.SkuResponse toSku(ResourceSkuEntity sku) {
        return new ResourceDetailResponse.SkuResponse(sku.getId(), sku.getSkuCode(), sku.getName(),
                sku.getPriceCent(), "CNY", sku.getAttributes());
    }
}
