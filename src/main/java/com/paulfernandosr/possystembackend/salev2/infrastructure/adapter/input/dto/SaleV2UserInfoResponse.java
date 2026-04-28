package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2UserInfoResponse {

    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
}
