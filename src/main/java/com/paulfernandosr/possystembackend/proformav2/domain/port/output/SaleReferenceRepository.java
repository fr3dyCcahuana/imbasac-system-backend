package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

public interface SaleReferenceRepository {
    void create(Long saleId, Long proformaId);
}
