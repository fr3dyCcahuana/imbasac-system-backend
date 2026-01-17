package com.paulfernandosr.possystembackend.purchase.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFlags {
    private Long id;
    private Boolean manageBySerial;
    private Boolean affectsStock;
    private String category;
}
