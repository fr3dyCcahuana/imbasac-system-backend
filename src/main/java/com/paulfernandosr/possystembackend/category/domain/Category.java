package com.paulfernandosr.possystembackend.category.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Category {
    private Long id;
    private String name;
    private String description;
}
