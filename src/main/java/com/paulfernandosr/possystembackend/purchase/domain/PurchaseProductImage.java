package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseProductImage {
    private Long id;
    private String imageUrl;
    private Boolean isMain;
    private Integer position;
}
