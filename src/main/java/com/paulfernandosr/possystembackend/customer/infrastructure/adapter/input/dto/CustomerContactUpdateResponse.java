package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerContactUpdateResponse {
    private Long customerId;
    private String phone;
    private String email;
}
