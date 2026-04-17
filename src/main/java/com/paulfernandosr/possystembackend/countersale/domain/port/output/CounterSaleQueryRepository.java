package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;

import java.util.List;

public interface CounterSaleQueryRepository {
    long countCounterSales(String likeParam,
                           String series,
                           Long number,
                           String status);

    List<CounterSaleSummaryResponse> findCounterSalesPage(String likeParam,
                                                          String series,
                                                          Long number,
                                                          String status,
                                                          int limit,
                                                          int offset);

    List<CounterSaleSummaryItemResponse> findSummaryItemsByCounterSaleIds(List<Long> counterSaleIds);

    CounterSaleDetailResponse findCounterSaleDetail(Long counterSaleId);

    List<CounterSaleItemResponse> findCounterSaleItems(Long counterSaleId);

    List<CounterSaleSerialUnitResponse> findCounterSaleItemSerialUnits(Long counterSaleId);

    CounterSalePaymentResponse findCounterSalePayment(Long counterSaleId);

    ElectronicReceiptPrintableResponse findElectronicReceiptPrintableHeaderByComboId(Long comboId);

    List<ElectronicReceiptPrintableItemResponse> findElectronicReceiptPrintableItemsBySaleId(Long saleId);
}
