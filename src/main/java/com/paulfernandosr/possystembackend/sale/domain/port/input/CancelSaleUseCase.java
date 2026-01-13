package com.paulfernandosr.possystembackend.sale.domain.port.input;

public interface CancelSaleUseCase {
    void cancelSaleById(Long saleId, String username);
}
