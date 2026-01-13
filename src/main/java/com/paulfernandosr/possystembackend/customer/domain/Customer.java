package com.paulfernandosr.possystembackend.customer.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    private Long id;
    private String legalName;
    private DocumentType documentType;
    private String documentNumber;
    private String address;
    private boolean enabled;
}
