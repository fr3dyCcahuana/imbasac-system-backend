package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.sunat;

public record ParsedSunatAddress(
        String address,
        String ubigeo,
        String department,
        String province,
        String district
) {
}
