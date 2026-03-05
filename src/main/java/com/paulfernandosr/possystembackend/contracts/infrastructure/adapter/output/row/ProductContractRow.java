package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductContractRow {
    private Long id;
    private String sku;
    private String name;
    private String category;
    private Boolean manageBySerial;
    private Boolean affectsStock;
    private String brand;
    private String model;
}
