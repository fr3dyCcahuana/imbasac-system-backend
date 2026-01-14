package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;

import java.util.List;

public interface SaleV2QueryRepository {

    long countSales(String likeParam);

    List<SaleV2SummaryResponse> findSalesPage(String likeParam, int limit, int offset);

    SaleV2DetailResponse findSaleDetail(Long saleId);

    List<SaleV2ItemResponse> findSaleItems(Long saleId);

    SaleV2PaymentResponse findSalePayment(Long saleId);

    AccountsReceivableInfoResponse findReceivableBySaleId(Long saleId);

    List<AccountsReceivablePaymentInfo> findReceivablePayments(Long arId);
}
