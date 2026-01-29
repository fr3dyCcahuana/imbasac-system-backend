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

    // porcentajes
    private BigDecimal pctPublicA;
    private BigDecimal pctPublicB;
    private BigDecimal pctWholesaleC;
    private BigDecimal pctWholesaleD;

    private Boolean dryRun;
    private Boolean atomic;
}
