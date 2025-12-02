package com.paulfernandosr.possystembackend.common.domain;

import lombok.*;

import java.util.Collection;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page<T> {
    private Collection<T> content;
    private int number;
    private int size;
    private int numberOfElements;
    private long totalElements;
    private int totalPages;
}
