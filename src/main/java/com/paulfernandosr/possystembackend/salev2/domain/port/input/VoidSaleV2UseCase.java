package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.domain.model.VoidSaleV2Response;

public interface VoidSaleV2UseCase {
    VoidSaleV2Response voidSale(Long saleId, String reason);
}
