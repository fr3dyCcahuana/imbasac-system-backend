package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SerialUnitContractRow {
    private Long id;
    private Long productId;
    private String status;
    private String vin;
    private Long contractId;
}
