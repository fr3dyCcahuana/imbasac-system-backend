package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.AdminEditSaleV2BeforeSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2CounterSaleCompositionRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2Repository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2AdminEditRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleV2CounterSaleCompositionPersistenceService {

    private final SaleV2Repository saleV2Repository;
    private final SaleV2CounterSaleCompositionRepository compositionRepository;
    private final AdminEditSaleV2BeforeSunatUseCase adminEditSaleV2BeforeSunatUseCase;

    @Transactional
    public void reserveAndApply(Long saleId,
                                String username,
                                Long userId,
                                SaleV2CounterSaleCompositionCalculator.CompositionPlan plan,
                                SaleV2AdminEditRequest adminEditRequest) {
        SaleV2Repository.LockedEditableSale lockedSale = saleV2Repository.lockEditableById(saleId);
        if (lockedSale == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        List<Long> counterSaleIds = plan.getSources().stream()
                .map(SaleV2CounterSaleCompositionCalculator.SourceCounterSale::getCounterSaleId)
                .toList();

        List<SaleV2CounterSaleCompositionRepository.LockedCounterSaleForComposition> lockedCounterSales =
                compositionRepository.lockCounterSales(counterSaleIds);

        Map<Long, SaleV2CounterSaleCompositionRepository.LockedCounterSaleForComposition> byId = new LinkedHashMap<>();
        for (SaleV2CounterSaleCompositionRepository.LockedCounterSaleForComposition item : lockedCounterSales) {
            byId.put(item.getId(), item);
        }

        if (byId.size() != counterSaleIds.size()) {
            Set<Long> missing = counterSaleIds.stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            missing.removeAll(byId.keySet());
            throw new InvalidSaleV2Exception("No se pudo bloquear todos los counter-sale seleccionados: " + missing);
        }

        for (SaleV2CounterSaleCompositionCalculator.SourceCounterSale source : plan.getSources()) {
            SaleV2CounterSaleCompositionRepository.LockedCounterSaleForComposition locked = byId.get(source.getCounterSaleId());
            if (locked == null) {
                throw new InvalidSaleV2Exception("Counter-sale no encontrado: " + source.getCounterSaleId());
            }
            if (!"EMITIDA".equalsIgnoreCase(locked.getStatus())) {
                throw new InvalidSaleV2Exception(
                        "Counter-sale no disponible para composición. counterSaleId=" + locked.getId() + ", status=" + locked.getStatus()
                );
            }
            if (Boolean.TRUE.equals(locked.getAssociatedToSunat())) {
                throw new InvalidSaleV2Exception("Counter-sale ya asociado a SUNAT. counterSaleId=" + locked.getId());
            }

            compositionRepository.reserveCounterSale(
                    saleId,
                    locked.getId(),
                    locked.getTotal(),
                    locked.getDiscountTotal(),
                    userId,
                    username,
                    plan.getRequestedEditReason()
            );
        }

        adminEditSaleV2BeforeSunatUseCase.edit(saleId, adminEditRequest, username);
    }

    @Transactional
    public void finalizeAccepted(Long saleId,
                                 SaleV2CounterSaleCompositionCalculator.CompositionPlan plan,
                                 String emittedDocType,
                                 String emittedSeries,
                                 Long emittedNumber) {
        for (SaleV2CounterSaleCompositionCalculator.SourceCounterSale source : plan.getSources()) {
            compositionRepository.finalizeAcceptedCounterSale(
                    saleId,
                    source.getCounterSaleId(),
                    emittedDocType,
                    emittedSeries,
                    emittedNumber
            );
        }

        for (SaleV2CounterSaleCompositionCalculator.ComposedLine line : plan.getLines()) {
            if (!"COUNTER_SALE".equalsIgnoreCase(line.getSourceType())) {
                continue;
            }
            compositionRepository.insertAcceptedCounterSaleItem(
                    saleId,
                    line.getSourceDocumentId(),
                    line.getSourceItemId(),
                    line.getSourceLineNumber(),
                    line.getProductId(),
                    line.getSku(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getOriginalUnitPrice(),
                    line.getComposedUnitPrice(),
                    line.getOriginalRevenueTotal(),
                    line.getComposedRevenueTotal()
            );
        }
    }

    @Transactional
    public void releaseAndRestore(Long saleId,
                                  String username,
                                  SaleV2CounterSaleCompositionCalculator.CompositionPlan plan,
                                  SaleV2AdminEditRequest restoreRequest,
                                  String reason) {
        release(saleId, plan, reason);
        adminEditSaleV2BeforeSunatUseCase.edit(saleId, restoreRequest, username);
    }

    @Transactional
    public void release(Long saleId,
                        SaleV2CounterSaleCompositionCalculator.CompositionPlan plan,
                        String reason) {
        List<Long> counterSaleIds = plan.getSources().stream()
                .map(SaleV2CounterSaleCompositionCalculator.SourceCounterSale::getCounterSaleId)
                .toList();
        if (!counterSaleIds.isEmpty()) {
            compositionRepository.releaseCounterSales(saleId, counterSaleIds, reason);
        }
    }
}
