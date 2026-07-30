package com.beifanghui.backend.catalog.infrastructure;

import com.beifanghui.backend.catalog.domain.ResourceSkuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceSkuJpaRepository extends JpaRepository<ResourceSkuEntity, Long> {
    List<ResourceSkuEntity> findByResourceIdAndStatusOrderByPriceCentAsc(Long resourceId, String status);
    Optional<ResourceSkuEntity> findByIdAndStatus(Long id, String status);
}
