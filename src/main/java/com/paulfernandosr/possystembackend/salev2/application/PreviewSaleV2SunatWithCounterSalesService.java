package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleDetailResponse;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.PreviewSaleV2SunatWithCounterSalesUseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PreviewSaleV2SunatWithCounterSalesService implements PreviewSaleV2SunatWithCounterSalesUseCase {

    private final GetSaleV2UseCase getSaleV2UseCase;
    private final GetCounterSaleUseCase getCounterSaleUseCase;
    private final SaleV2CounterSaleCompositionCalculator calculator;

    @Override
    public SaleV2ComposeSunatPreviewResponse preview(Long saleId, SaleV2ComposeSunatRequest request) {
        SaleV2DetailResponse sale = getSaleV2UseCase.getById(saleId);
        List<CounterSaleDetailResponse> counterSales = new ArrayList<>();
        if (request != null && request.getCounterSales() != null) {
            for (SaleV2ComposeSunatRequest.CounterSaleSelection selection : request.getCounterSales()) {
                if (selection != null && selection.getCounterSaleId() != null) {
                    counterSales.add(getCounterSaleUseCase.getById(selection.getCounterSaleId()));
                }
            }
        }

        var plan = calculator.buildPlan(sale, counterSales, request, false);
        return SaleV2ComposeSunatPreviewResponse.builder()
                .saleId(plan.getSaleId())
                .docType(plan.getDocType())
                .series(plan.getSeries())
                .number(plan.getNumber())
                .taxStatus(plan.getTaxStatus())
                .igvRate(plan.getIgvRate())
                .igvIncluded(plan.isIgvIncluded())
                .originalSaleTotal(plan.getOriginalSaleTotal())
                .composedSubtotal(plan.getTotals().getSubtotal())
                .composedDiscountTotal(plan.getTotals().getDiscountTotal())
                .composedIgvAmount(plan.getTotals().getIgvAmount())
                .composedTotal(plan.getTotals().getTotal())
                .difference(plan.getDifference())
                .exactTotalMatch(plan.isExactTotalMatch())
                .counterSales(plan.getSources().stream().map(source -> SaleV2ComposeSunatSourceResponse.builder()
                        .counterSaleId(source.getCounterSaleId())
                        .series(source.getSeries())
                        .number(source.getNumber())
                        .status(source.getStatus())
                        .total(source.getTotal())
                        .discountTotal(source.getDiscountTotal())
                        .associatedToSunat(source.getAssociatedToSunat())
                        .associatedDocType(source.getAssociatedDocType())
                        .associatedSeries(source.getAssociatedSeries())
                        .associatedNumber(source.getAssociatedNumber())
                        .associatedAt(source.getAssociatedAt())
                        .build()).toList())
                .lines(plan.getLines().stream().map(line -> SaleV2ComposeSunatLineResponse.builder()
                        .sourceType(line.getSourceType())
                        .sourceDocumentLabel(line.getSourceDocumentLabel())
                        .sourceDocumentId(line.getSourceDocumentId())
                        .sourceItemId(line.getSourceItemId())
                        .sourceLineNumber(line.getSourceLineNumber())
                        .productId(line.getProductId())
                        .sku(line.getSku())
                        .description(line.getDescription())
                        .quantity(line.getQuantity())
                        .originalUnitPrice(line.getOriginalUnitPrice())
                        .composedUnitPrice(line.getComposedUnitPrice())
                        .discountPercent(line.getDiscountPercent())
                        .originalRevenueTotal(line.getOriginalRevenueTotal())
                        .composedRevenueTotal(line.getComposedRevenueTotal())
                        .build()).toList())
                .build();
    }
}
