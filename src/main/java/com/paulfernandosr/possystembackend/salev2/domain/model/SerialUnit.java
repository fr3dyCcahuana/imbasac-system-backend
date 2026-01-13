package com.paulfernandosr.possystembackend.salev2.domain.model;

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
}
