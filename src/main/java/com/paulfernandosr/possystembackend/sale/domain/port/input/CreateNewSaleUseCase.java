package com.paulfernandosr.possystembackend.sale.domain.port.input;

import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleDocument;

public interface CreateNewSaleUseCase {
    SaleDocument createNewSale(Sale sale);
}
