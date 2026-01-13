package com.paulfernandosr.possystembackend.sale.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleItem;
import com.paulfernandosr.possystembackend.sale.domain.SaleType;

import java.util.Collection;
import java.util.Optional;

public interface SaleRepository {
    void create(Sale sale);

    Optional<Sale> findById(Long id);

    //Collection<SaleItem> findFullSaleItemsBySaleId(Long saleId);

    Collection<SaleItem> findSaleItemsBySaleId(Long saleId);

    Long getNextNumberByType(SaleType saleType);

    Page<Sale> findPage(String query, String type, Pageable pageable);

    void cancel(Sale sale);
}
