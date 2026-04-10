package com.paulfernandosr.possystembackend.countersale.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenSaleSession {
    private Long id;
    private Long userId;
    private Long stationId;
}
