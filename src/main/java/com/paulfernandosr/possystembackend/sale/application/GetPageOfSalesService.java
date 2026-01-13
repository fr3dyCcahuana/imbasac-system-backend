package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.port.input.GetPageOfSalesUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfSalesService implements GetPageOfSalesUseCase {
    private final SaleRepository saleRepository;

    @Override
    public Page<Sale> getPageOfSales(String query, String type, Pageable pageable) {
        Page<Sale> pageOfSales = saleRepository.findPage(query,type,pageable);

        pageOfSales.getContent().forEach(sale -> {
            sale.setItems(saleRepository.findSaleItemsBySaleId(sale.getId()));
        });

        return pageOfSales;
    }
}
