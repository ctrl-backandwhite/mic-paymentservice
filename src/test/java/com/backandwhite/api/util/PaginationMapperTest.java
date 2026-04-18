package com.backandwhite.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.backandwhite.api.dto.PaginationDtoOut;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaginationMapperTest {

    @Test
    void map_transformsContent() {
        PaginationDtoOut<Integer> source = PaginationDtoOut.<Integer>builder().content(List.of(1, 2, 3))
                .totalElements(3L).totalPages(1).currentPage(0).pageSize(10).hasNext(false).hasPrevious(false).build();

        PaginationDtoOut<String> result = PaginationMapper.map(source, String::valueOf);

        assertThat(result.getContent()).containsExactly("1", "2", "3");
        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getCurrentPage()).isZero();
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.isHasPrevious()).isFalse();
    }
}
