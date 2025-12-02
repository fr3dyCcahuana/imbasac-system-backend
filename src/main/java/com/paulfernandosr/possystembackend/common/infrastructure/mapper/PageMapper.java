package com.paulfernandosr.possystembackend.common.infrastructure.mapper;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;

public class PageMapper {
    public static <T> SuccessResponse.Metadata mapPage(Page<T> page) {
        return SuccessResponse.Metadata.builder()
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .numberOfElements(page.getNumberOfElements())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }
}
