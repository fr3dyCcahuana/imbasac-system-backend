package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    private Long id;
    private Long productId;

    private String imageUrl;
    private Short position;       // 1–5
    private Boolean isMain;       // true si es portada

    private LocalDateTime createdAt;
}
