package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2QueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSaleV2Service implements GetSaleV2UseCase {

    private final SaleV2QueryRepository saleV2QueryRepository;

    @Override
    public SaleV2DetailResponse getById(Long saleId) {
        SaleV2DetailResponse header = saleV2QueryRepository.findSaleDetail(saleId);
        if (header == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        List<SaleV2ItemResponse> items = saleV2QueryRepository.findSaleItems(saleId);
        header.setItems(items);

        if ("CONTADO".equalsIgnoreCase(header.getPaymentType())) {
            header.setPayment(saleV2QueryRepository.findSalePayment(saleId));
        } else if ("CREDITO".equalsIgnoreCase(header.getPaymentType())) {
            AccountsReceivableInfoResponse ar = saleV2QueryRepository.findReceivableBySaleId(saleId);
            header.setReceivable(ar);
            if (ar != null && ar.getArId() != null) {
                header.setReceivablePayments(saleV2QueryRepository.findReceivablePayments(ar.getArId()));
            }
        }

        return header;
    }
}
