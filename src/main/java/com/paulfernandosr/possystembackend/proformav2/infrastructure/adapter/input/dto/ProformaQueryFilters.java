package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import java.time.LocalDate;

/**
 * Filtros del historial de proformas. {@code like} ya viene formateado con %...%.
 */
public record ProformaQueryFilters(
        String status,
        String like,
        Long createdBy,
        Long createdByRoleId,
        Boolean edited,
        String paymentType,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
