package com.paulfernandosr.possystembackend.catalog.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class District {
    private Long id;
    private String code;
    private String departmentCode;
    private String provinceCode;
    private String name;
}
