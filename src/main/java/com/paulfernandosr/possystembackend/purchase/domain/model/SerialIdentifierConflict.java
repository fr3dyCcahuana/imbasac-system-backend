package com.paulfernandosr.possystembackend.purchase.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerialIdentifierConflict {
    private Long id;
    private Long productId;
    private String vin;
    private String engineNumber;
    private String serialNumber;
}
