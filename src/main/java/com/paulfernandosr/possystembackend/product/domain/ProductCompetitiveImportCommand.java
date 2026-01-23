package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCompetitiveImportCommand {
    private byte[] fileBytes;
    private String originalFilename;

    private BigDecimal montoRestaPublico;
    private BigDecimal montoRestaMayorista;

    private Boolean dryRun;
    private Boolean atomic;
}
