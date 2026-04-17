package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetElectronicReceiptPrintableUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.CounterSaleQueryRepository;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.ElectronicReceiptPrintableResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetElectronicReceiptPrintableService implements GetElectronicReceiptPrintableUseCase {

    private final CounterSaleQueryRepository counterSaleQueryRepository;

    @Override
    public ElectronicReceiptPrintableResponse getByComboId(Long comboId) {
        ElectronicReceiptPrintableResponse response = counterSaleQueryRepository.findElectronicReceiptPrintableHeaderByComboId(comboId);
        if (response == null) {
            throw new InvalidCounterSaleException("Comprobante electrónico de venta diaria no encontrado: " + comboId);
        }
        response.setItems(counterSaleQueryRepository.findElectronicReceiptPrintableItemsBySaleId(response.getSaleId()));
        return response;
    }
}
