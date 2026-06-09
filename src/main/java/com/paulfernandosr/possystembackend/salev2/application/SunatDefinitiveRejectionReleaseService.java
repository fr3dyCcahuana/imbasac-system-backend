package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.salev2.domain.model.StockMovementBalance;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2Repository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SunatDefinitiveRejectionReleaseService {

    private final JdbcClient jdbcClient;
    private final SaleV2Repository saleRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final ProformaItemRepository proformaItemRepository;

    @Transactional
    public void release(Long saleId, String reason) {
        if (saleId == null) {
            return;
        }

        if (hasCompositionRollbackManagedElsewhere(saleId)) {
            return;
        }

        RejectionReleaseSale sale = lockSale(saleId);
        if (sale == null) {
            return;
        }

        List<SaleV2Repository.SaleItemForVoid> items = saleRepository.findItemsBySaleId(saleId);
        releaseSerialUnits(items);
        releaseSaleStock(items);
        releaseInternalProformaStock(sale.getSourceProformaId());
        reopenSourceProforma(sale.getSourceProformaId(), saleId);

        String note = "ANULADA AUTOMATICAMENTE POR RECHAZO DEFINITIVO SUNAT";
        if (reason != null && !reason.isBlank()) {
            note += ": " + truncate(reason, 3500);
        }
        saleRepository.markAsVoided(saleId, note);
    }

    private void releaseSerialUnits(List<SaleV2Repository.SaleItemForVoid> items) {
        List<Long> saleItemIds = items.stream()
                .map(SaleV2Repository.SaleItemForVoid::getId)
                .toList();

        var serials = productSerialUnitRepository.lockBySaleItemIds(saleItemIds);
        for (var serial : serials) {
            productSerialUnitRepository.releaseFromSaleForEdition(serial.getId());
        }
    }

    private void releaseSaleStock(List<SaleV2Repository.SaleItemForVoid> items) {
        for (var item : items) {
            if (!Boolean.TRUE.equals(item.getAffectsStock())) {
                continue;
            }

            BigDecimal quantity = nz(item.getQuantity());
            if (quantity.signum() <= 0) {
                continue;
            }

            if (!productStockMovementRepository.existsOutboundSaleItem(item.getId())
                    || productStockMovementRepository.existsInReturnSaleItem(item.getId())) {
                continue;
            }

            StockMovementBalance balance = productStockRepository.increaseOnHand(item.getProductId(), quantity);
            BigDecimal unitCost = nz(item.getUnitCostSnapshot());
            BigDecimal totalCost = item.getTotalCostSnapshot() != null
                    ? item.getTotalCostSnapshot()
                    : unitCost.multiply(quantity);

            productStockMovementRepository.createInReturn(
                    item.getProductId(),
                    quantity,
                    item.getId(),
                    unitCost,
                    totalCost,
                    balance.getQuantityOnHand(),
                    nz(balance.getAverageCost(), unitCost)
            );
        }
    }

    private void releaseInternalProformaStock(Long sourceProformaId) {
        if (sourceProformaId == null) {
            return;
        }

        List<ProformaItem> proformaItems = proformaItemRepository.findByProformaId(sourceProformaId);
        if (proformaItems == null || proformaItems.isEmpty()) {
            return;
        }

        for (ProformaItem item : proformaItems) {
            if (Boolean.TRUE.equals(item.getFacturableSunat())
                    || !Boolean.TRUE.equals(item.getAffectsStock())) {
                continue;
            }

            BigDecimal quantity = nz(item.getQuantity());
            if (quantity.signum() <= 0) {
                continue;
            }

            if (!productStockMovementRepository.existsOutProformaInternal(item.getId())
                    || productStockMovementRepository.existsInProformaInternalReturn(item.getId())) {
                continue;
            }

            BigDecimal unitCost = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            StockMovementBalance balance = productStockRepository.increaseOnHand(item.getProductId(), quantity);

            productStockMovementRepository.createInProformaInternalReturn(
                    item.getProductId(),
                    quantity,
                    item.getId(),
                    unitCost,
                    totalCost,
                    balance.getQuantityOnHand(),
                    nz(balance.getAverageCost(), unitCost)
            );
        }
    }

    private void reopenSourceProforma(Long sourceProformaId, Long saleId) {
        if (sourceProformaId == null) {
            return;
        }

        String sql = """
            UPDATE proforma
               SET status = 'PENDIENTE',
                   converted_sale_id = NULL,
                   converted_at = NULL,
                   converted_by = NULL,
                   updated_at = NOW()
             WHERE id = ?
               AND converted_sale_id = ?
               AND status = 'CONVERTIDA'
        """;

        jdbcClient.sql(sql)
                .params(sourceProformaId, saleId)
                .update();
    }

    private boolean hasCompositionRollbackManagedElsewhere(Long saleId) {
        String sql = """
            SELECT COUNT(1)
              FROM sale_counter_sale_sunat_link
             WHERE sale_id = ?
               AND reservation_status IN ('PENDING', 'ERROR_COMUNICACION', 'RECHAZADO')
        """;

        Long count = jdbcClient.sql(sql)
                .param(saleId)
                .query(Long.class)
                .single();

        return count != null && count > 0;
    }

    private RejectionReleaseSale lockSale(Long saleId) {
        String sql = """
            SELECT id,
                   source_proforma_id
              FROM sale
             WHERE id = ?
             FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> RejectionReleaseSale.builder()
                        .id(rs.getLong("id"))
                        .sourceProformaId((Long) rs.getObject("source_proforma_id"))
                        .build())
                .optional()
                .orElse(null);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal nz(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Getter
    @Builder
    private static class RejectionReleaseSale {
        private Long id;
        private Long sourceProformaId;
    }
}
