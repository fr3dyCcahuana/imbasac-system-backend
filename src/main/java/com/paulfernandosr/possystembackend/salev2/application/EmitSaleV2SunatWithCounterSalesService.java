package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleDetailResponse;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitSaleV2SunatWithCounterSalesUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitSaleV2ToSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2AdminEditRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatEmitResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatSourceResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DetailResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatEmissionResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmitSaleV2SunatWithCounterSalesService implements EmitSaleV2SunatWithCounterSalesUseCase {

    private final GetSaleV2UseCase getSaleV2UseCase;
    private final GetCounterSaleUseCase getCounterSaleUseCase;
    private final UserRepository userRepository;
    private final SaleV2CounterSaleCompositionCalculator calculator;
    private final SaleV2CounterSaleCompositionPersistenceService persistenceService;
    private final EmitSaleV2ToSunatUseCase emitSaleV2ToSunatUseCase;

    @Override
    public SaleV2ComposeSunatEmitResponse emit(Long saleId, SaleV2ComposeSunatRequest request, String username) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es obligatorio.");
        }
        if (username == null || username.isBlank()) {
            throw new InvalidSaleV2Exception("username es obligatorio.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));

        SaleV2DetailResponse originalSale = getSaleV2UseCase.getById(saleId);
        List<CounterSaleDetailResponse> counterSales = loadCounterSales(request);

        SaleV2CounterSaleCompositionCalculator.CompositionPlan plan = calculator.buildPlan(originalSale, counterSales, request, true);
        if (!plan.isExactTotalMatch()) {
            throw new InvalidSaleV2Exception(
                    "La composición no mantiene el mismo total de la venta original. Diferencia=" + plan.getDifference()
            );
        }

        String paymentMethod = originalSale.getPayment() != null ? originalSale.getPayment().getMethod() : null;
        SaleV2AdminEditRequest composedEditRequest = calculator.buildAdminEditRequest(originalSale, plan, paymentMethod);
        SaleV2AdminEditRequest restoreRequest = calculator.buildRestoreRequest(
                originalSale,
                "ROLLBACK_COMPOSICION_SUNAT_COUNTER_SALES"
        );

        persistenceService.reserveAndApply(saleId, username, user.getId(), plan, composedEditRequest);

        SaleV2SunatEmissionResponse emission;
        try {
            emission = emitSaleV2ToSunatUseCase.emit(saleId);
        } catch (RuntimeException ex) {
            persistenceService.releaseAndRestore(saleId, username, plan, restoreRequest, "ERROR_EMISION: " + safeMessage(ex));
            throw ex;
        }

        if (emission == null || !"ACEPTADO".equalsIgnoreCase(emission.getSunatStatus())) {
            String releaseReason = emission == null
                    ? "EMISION_SIN_RESPUESTA"
                    : "EMISION_NO_ACEPTADA: " + emission.getSunatStatus();
            persistenceService.releaseAndRestore(saleId, username, plan, restoreRequest, releaseReason);
            throw new InvalidSaleV2Exception(
                    emission == null
                            ? "SUNAT no devolvió respuesta para la emisión compuesta."
                            : "SUNAT no aceptó la emisión compuesta. sunatStatus=" + emission.getSunatStatus()
            );
        }

        persistenceService.finalizeAccepted(saleId, plan, emission.getDocType(), emission.getSeries(), emission.getNumber());

        return SaleV2ComposeSunatEmitResponse.builder()
                .saleId(plan.getSaleId())
                .docType(plan.getDocType())
                .series(plan.getSeries())
                .number(plan.getNumber())
                .originalSaleTotal(plan.getOriginalSaleTotal())
                .composedTotal(plan.getTotals().getTotal())
                .difference(plan.getDifference())
                .exactTotalMatch(plan.isExactTotalMatch())
                .emission(emission)
                .linkedCounterSales(plan.getSources().stream().map(source -> SaleV2ComposeSunatSourceResponse.builder()
                        .counterSaleId(source.getCounterSaleId())
                        .series(source.getSeries())
                        .number(source.getNumber())
                        .status(source.getStatus())
                        .total(source.getTotal())
                        .discountTotal(source.getDiscountTotal())
                        .associatedToSunat(true)
                        .associatedDocType(emission.getDocType())
                        .associatedSeries(emission.getSeries())
                        .associatedNumber(emission.getNumber())
                        .associatedAt(emission.getEmittedAt())
                        .build()).toList())
                .build();
    }

    private List<CounterSaleDetailResponse> loadCounterSales(SaleV2ComposeSunatRequest request) {
        List<CounterSaleDetailResponse> counterSales = new ArrayList<>();
        if (request == null || request.getCounterSales() == null) {
            return counterSales;
        }
        for (SaleV2ComposeSunatRequest.CounterSaleSelection selection : request.getCounterSales()) {
            if (selection != null && selection.getCounterSaleId() != null) {
                counterSales.add(getCounterSaleUseCase.getById(selection.getCounterSaleId()));
            }
        }
        return counterSales;
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }
}
