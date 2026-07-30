package com.beifanghui.backend.shared.api;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int totalPages) {
    public static <S, T> PageResponse<T> from(Page<S> source, List<T> items) {
        return new PageResponse<>(items, source.getNumber() + 1, source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
