package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvertProformaV2Response {
    private Long proformaId;
    private String proformaStatus;

    private Long saleId;
    private String saleDocType;
    private String saleSeries;
    private Long saleNumber;
}
