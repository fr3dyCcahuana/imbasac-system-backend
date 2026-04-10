package com.paulfernandosr.possystembackend.countersale.domain.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidCounterSaleResponse {
    private Long counterSaleId;
    private String status;
}
