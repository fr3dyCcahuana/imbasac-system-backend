package com.paulfernandosr.possystembackend.sale.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.sale.domain.Sale;

public interface GetPageOfSalesUseCase {
    Page<Sale> getPageOfSales(String query,String type, Pageable pageable);
}
