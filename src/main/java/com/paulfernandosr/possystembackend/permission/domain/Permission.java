package com.paulfernandosr.possystembackend.permission.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Permission {
    private Long id;
    private String name;
    private String domain;
    private String description;
}
