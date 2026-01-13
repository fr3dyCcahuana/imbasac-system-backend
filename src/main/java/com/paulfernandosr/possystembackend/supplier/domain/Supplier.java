package com.paulfernandosr.possystembackend.supplier.domain;

import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {
    private Long id;
    private String legalName;
    private DocumentType documentType;   // SOLO RUC
    private String documentNumber;
    private String address;
    private boolean enabled;
}
