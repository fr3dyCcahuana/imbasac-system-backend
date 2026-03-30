package com.paulfernandosr.possystembackend.catalog.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Department {
    private Long id;
    private String code;
    private String name;
}
