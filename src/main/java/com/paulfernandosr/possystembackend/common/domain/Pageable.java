package com.paulfernandosr.possystembackend.common.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pageable {
    private int number;
    private int size;
}
