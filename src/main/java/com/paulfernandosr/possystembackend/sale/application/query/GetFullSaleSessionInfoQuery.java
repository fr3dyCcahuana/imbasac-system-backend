package com.paulfernandosr.possystembackend.sale.application.query;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetFullSaleSessionInfoQuery {
    private Long saleSessionId;
    private String password;
}
