package com.paulfernandosr.possystembackend.salev2.domain.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidSaleV2Response {
    private Long saleId;
    private String status;
}
