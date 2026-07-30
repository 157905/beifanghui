package com.beifanghui.backend.catalog.infrastructure;

import com.beifanghui.backend.catalog.domain.ResourceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, Long> {
    Page<ResourceEntity> findByStatus(String status, Pageable pageable);
    Page<ResourceEntity> findByStatusAndResourceType(String status, String resourceType, Pageable pageable);
    Optional<ResourceEntity> findByIdAndStatus(Long id, String status);
}
