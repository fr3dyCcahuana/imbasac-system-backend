package com.paulfernandosr.possystembackend.catalog.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Presentation {
    private Long id;
    private String name;
}
