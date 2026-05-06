package com.paulfernandosr.possystembackend.proformav2.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerLocationSnapshot {
    private String ubigeo;
    private String department;
    private String province;
    private String district;
}
