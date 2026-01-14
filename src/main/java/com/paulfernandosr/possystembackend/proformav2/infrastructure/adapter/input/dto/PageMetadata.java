package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageMetadata {
    private int page;
    private int size;
    private int numberOfElements;
    private long totalElements;
    private int totalPages;
}
