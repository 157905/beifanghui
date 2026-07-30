package com.beifanghui.backend.catalog.infrastructure;

import com.beifanghui.backend.catalog.domain.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findBySkuIdAndBusinessDateAndTimeSlot(Long skuId, LocalDate businessDate, String timeSlot);
}
