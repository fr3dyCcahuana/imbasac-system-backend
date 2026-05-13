package com.paulfernandosr.possystembackend.salev2.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SaleV2SunatRelationFinalizerService {

    private final JdbcClient jdbcClient;

    @Transactional
    public void onAccepted(Long saleId, String docType, String series, Long number, LocalDateTime associatedAt) {
        LocalDateTime safeAssociatedAt = associatedAt == null ? LocalDateTime.now() : associatedAt;
        finalizeDirectCounterSaleCombo(saleId, docType, series, number, safeAssociatedAt);
        finalizeSaleCounterSaleComposition(saleId, docType, series, number, safeAssociatedAt);
    }

    @Transactional
    public void onNotAccepted(Long saleId, String status, String reason) {
        String relationStatus = normalizeRelationStatus(status);
        markDirectCounterSaleComboNotAccepted(saleId, relationStatus, reason);
        markSaleCounterSaleCompositionNotAccepted(saleId, relationStatus, reason);
    }

    private void finalizeDirectCounterSaleCombo(Long saleId, String docType, String series, Long number, LocalDateTime associatedAt) {
        String updateCombo = """
            UPDATE counter_sale_sunat_combo
               SET combo_status = 'ACEPTADO',
                   emitted_doc_type = ?,
                   emitted_series = ?,
                   emitted_number = ?,
                   associated_at = ?,
                   error_message = NULL,
                   updated_at = NOW()
             WHERE generated_sale_id = ?
               AND combo_status IN ('PENDING', 'ERROR', 'ERROR_COMUNICACION', 'RECHAZADO')
        """;

        jdbcClient.sql(updateCombo)
                .params(docType, series, number, associatedAt, saleId)
                .update();

        String updateCounterSales = """
            UPDATE counter_sale cs
               SET associated_to_sunat = TRUE,
                   associated_sale_id = ?,
                   associated_doc_type = ?,
                   associated_series = ?,
                   associated_number = ?,
                   associated_at = ?,
                   updated_at = NOW()
              FROM counter_sale_sunat_combo c
              JOIN counter_sale_sunat_combo_member m
                ON m.combo_id = c.id
             WHERE c.generated_sale_id = ?
               AND c.combo_status = 'ACEPTADO'
               AND m.counter_sale_id = cs.id
               AND cs.status = 'EMITIDA'
               AND COALESCE(cs.associated_to_sunat, FALSE) = FALSE
        """;

        jdbcClient.sql(updateCounterSales)
                .params(saleId, docType, series, number, associatedAt, saleId)
                .update();
    }

    private void finalizeSaleCounterSaleComposition(Long saleId, String docType, String series, Long number, LocalDateTime associatedAt) {
        String updateLinks = """
            UPDATE sale_counter_sale_sunat_link
               SET reservation_status = 'ACEPTADO',
                   emitted_doc_type = ?,
                   emitted_series = ?,
                   emitted_number = ?,
                   associated_at = ?,
                   release_reason = NULL,
                   updated_at = NOW()
             WHERE sale_id = ?
               AND reservation_status IN ('PENDING', 'ERROR_COMUNICACION')
        """;

        jdbcClient.sql(updateLinks)
                .params(docType, series, number, associatedAt, saleId)
                .update();

        String updateCounterSales = """
            UPDATE counter_sale cs
               SET associated_to_sunat = TRUE,
                   associated_sale_id = ?,
                   associated_doc_type = ?,
                   associated_series = ?,
                   associated_number = ?,
                   associated_at = ?,
                   updated_at = NOW()
              FROM sale_counter_sale_sunat_link l
             WHERE l.sale_id = ?
               AND l.reservation_status = 'ACEPTADO'
               AND l.counter_sale_id = cs.id
               AND cs.status = 'EMITIDA'
               AND COALESCE(cs.associated_to_sunat, FALSE) = FALSE
        """;

        jdbcClient.sql(updateCounterSales)
                .params(saleId, docType, series, number, associatedAt, saleId)
                .update();
    }

    private void markDirectCounterSaleComboNotAccepted(Long saleId, String status, String reason) {
        String sql = """
            UPDATE counter_sale_sunat_combo
               SET combo_status = ?,
                   error_message = ?,
                   updated_at = NOW()
             WHERE generated_sale_id = ?
               AND combo_status IN ('PENDING', 'ERROR', 'ERROR_COMUNICACION', 'RECHAZADO')
        """;

        jdbcClient.sql(sql)
                .params(status, truncate(reason, 4000), saleId)
                .update();
    }

    private void markSaleCounterSaleCompositionNotAccepted(Long saleId, String status, String reason) {
        String sql = """
            UPDATE sale_counter_sale_sunat_link
               SET reservation_status = ?,
                   release_reason = ?,
                   updated_at = NOW()
             WHERE sale_id = ?
               AND reservation_status IN ('PENDING', 'ERROR_COMUNICACION')
        """;

        jdbcClient.sql(sql)
                .params(status, truncate(reason, 4000), saleId)
                .update();
    }

    private String normalizeRelationStatus(String sunatStatus) {
        String value = sunatStatus == null ? "" : sunatStatus.trim().toUpperCase();
        if ("ERROR_COMUNICACION".equals(value)) return "ERROR_COMUNICACION";
        if ("RECHAZADO".equals(value)) return "RECHAZADO";
        return "ERROR";
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
