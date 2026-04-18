package com.backandwhite.api.util;

import com.backandwhite.api.dto.PaginationDtoOut;
import java.util.function.Function;

public final class PaginationMapper {

    private PaginationMapper() {
    }

    public static <S, T> PaginationDtoOut<T> map(PaginationDtoOut<S> source, Function<S, T> mapper) {
        return PaginationDtoOut.<T>builder().content(source.getContent().stream().map(mapper).toList())
                .totalElements(source.getTotalElements()).totalPages(source.getTotalPages())
                .currentPage(source.getCurrentPage()).pageSize(source.getPageSize()).hasNext(source.isHasNext())
                .hasPrevious(source.isHasPrevious()).build();
    }
}
