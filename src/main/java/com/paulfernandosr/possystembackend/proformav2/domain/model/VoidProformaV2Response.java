package com.paulfernandosr.possystembackend.proformav2.domain.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidProformaV2Response {
    private Long proformaId;
    private String status;
}
