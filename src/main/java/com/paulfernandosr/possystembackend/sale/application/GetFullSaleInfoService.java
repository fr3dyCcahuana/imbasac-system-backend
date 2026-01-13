package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.exception.InvalidSaleException;
import com.paulfernandosr.possystembackend.sale.domain.port.input.GetFullSaleInfoUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetFullSaleInfoService implements GetFullSaleInfoUseCase {
    private final SaleRepository saleRepository;

    @Override
    public Sale getFullSaleInfoById(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new InvalidSaleException("Invalid sale with identification: " + saleId));

        //sale.setItems(saleRepository.findFullSaleItemsBySaleId(saleId));

        return sale;
    }
}
