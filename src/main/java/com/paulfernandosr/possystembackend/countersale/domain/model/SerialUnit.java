package com.paulfernandosr.possystembackend.countersale.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerialUnit {
    private Long id;
    private Long productId;
    private String status;
    private String vin;
    private String chassisNumber;
    private String engineNumber;
    private String color;
    private Integer yearMake;
    private Long contractId;
}
