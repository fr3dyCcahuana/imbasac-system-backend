package com.paulfernandosr.possystembackend.purchase.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    /**
     * Para MOTOCICLETAS: número de chasis.
     * Alias por compatibilidad con campo anterior "serialNumber".
     */
    @JsonAlias({"serialNumber"})
    private String chassisNumber;
}
