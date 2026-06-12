package com.paulfernandosr.possystembackend.stockreservation.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.stockreservation.domain.port.output.ProductStockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockReservationRepository implements ProductStockReservationRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void replaceActiveForProforma(Long proformaId, Long reservedBy) {
        if (proformaId == null) {
            throw new StockReservationException("proformaId requerido para reservar stock.");
        }

        expirePreviousDays();
        releaseActiveForProforma(proformaId);

        List<ReservationNeed> needs = findReservationNeeds(proformaId);
        for (ReservationNeed need : needs) {
            BigDecimal onHand = lockOnHand(need.productId());
            BigDecimal reserved = currentReservedQuantity(need.productId());
            BigDecimal available = onHand.subtract(reserved);

            if (available.compareTo(need.quantity()) < 0) {
                throw new StockReservationException(
                        "Stock insuficiente para reservar proforma. SKU=" + safe(need.sku())
                                + ", solicitado=" + need.quantity()
                                + ", stock=" + onHand
                                + ", reservado=" + reserved
                                + ", disponible=" + available
                );
            }
        }

        insertReservations(proformaId, reservedBy);
    }

    @Override
    public void consumeActiveForProforma(Long proformaId) {
        if (proformaId == null) {
            return;
        }
        expirePreviousDays();

        String sql = """
            UPDATE product_stock_reservation
               SET status = 'CONSUMED',
                   consumed_at = NOW(),
                   updated_at = NOW()
             WHERE proforma_id = ?
               AND status = 'ACTIVE'
               AND reserved_date = CURRENT_DATE
            """;

        jdbcClient.sql(sql)
                .param(proformaId)
                .update();
    }

    @Override
    public void releaseActiveForProforma(Long proformaId) {
        if (proformaId == null) {
            return;
        }

        String sql = """
            UPDATE product_stock_reservation
               SET status = 'RELEASED',
                   released_at = NOW(),
                   updated_at = NOW()
             WHERE proforma_id = ?
               AND status = 'ACTIVE'
            """;

        jdbcClient.sql(sql)
                .param(proformaId)
                .update();
    }

    @Override
    public int expirePreviousDays() {
        String sql = """
            UPDATE product_stock_reservation
               SET status = 'EXPIRED',
                   released_at = NOW(),
                   updated_at = NOW()
             WHERE status = 'ACTIVE'
               AND reserved_date < CURRENT_DATE
            """;

        return jdbcClient.sql(sql).update();
    }

    private List<ReservationNeed> findReservationNeeds(Long proformaId) {
        String sql = """
            SELECT
              pi.product_id,
              MIN(pi.sku) AS sku,
              SUM(pi.quantity) AS quantity
            FROM proforma_item pi
            JOIN product p ON p.id = pi.product_id
            WHERE pi.proforma_id = ?
              AND COALESCE(pi.affects_stock, TRUE) = TRUE
              AND COALESCE(p.affects_stock, TRUE) = TRUE
              AND COALESCE(p.manage_by_serial, FALSE) = FALSE
            GROUP BY pi.product_id
            ORDER BY pi.product_id
            """;

        return jdbcClient.sql(sql)
                .param(proformaId)
                .query((rs, rowNum) -> new ReservationNeed(
                        rs.getLong("product_id"),
                        rs.getString("sku"),
                        rs.getBigDecimal("quantity")
                ))
                .list();
    }

    private BigDecimal lockOnHand(Long productId) {
        String sql = """
            SELECT quantity_on_hand
              FROM product_stock
             WHERE product_id = ?
             FOR UPDATE
            """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal currentReservedQuantity(Long productId) {
        String sql = """
            SELECT COALESCE(SUM(quantity), 0)
              FROM product_stock_reservation
             WHERE product_id = ?
               AND status = 'ACTIVE'
               AND reserved_date = CURRENT_DATE
            """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .single();
    }

    private void insertReservations(Long proformaId, Long reservedBy) {
        String sql = """
            INSERT INTO product_stock_reservation(
              product_id,
              proforma_id,
              proforma_item_id,
              quantity,
              reserved_date,
              expires_at,
              status,
              reserved_by,
              created_at,
              updated_at
            )
            SELECT
              pi.product_id,
              pi.proforma_id,
              pi.id,
              pi.quantity,
              CURRENT_DATE,
              CURRENT_DATE + INTERVAL '1 day',
              'ACTIVE',
              ?,
              NOW(),
              NOW()
            FROM proforma_item pi
            JOIN product p ON p.id = pi.product_id
            WHERE pi.proforma_id = ?
              AND COALESCE(pi.affects_stock, TRUE) = TRUE
              AND COALESCE(p.affects_stock, TRUE) = TRUE
              AND COALESCE(p.manage_by_serial, FALSE) = FALSE
            ORDER BY pi.product_id, pi.id
            """;

        jdbcClient.sql(sql)
                .params(reservedBy, proformaId)
                .update();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "SIN_SKU" : value;
    }

    private record ReservationNeed(Long productId, String sku, BigDecimal quantity) {
    }
}
