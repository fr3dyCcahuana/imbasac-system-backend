package com.paulfernandosr.possystembackend.stockreservation.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.stockreservation.application.StockReservationAllocationPolicy;
import com.paulfernandosr.possystembackend.stockreservation.application.StockReservationAllocationPolicy.Allocation;
import com.paulfernandosr.possystembackend.stockreservation.application.StockReservationAllocationPolicy.ReservationState;
import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.stockreservation.domain.model.StockReservationReassignment;
import com.paulfernandosr.possystembackend.stockreservation.domain.port.output.ProductStockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockReservationRepository implements ProductStockReservationRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void replaceActiveForProforma(Long proformaId, Long reservedBy) {
        if (proformaId == null) {
            throw new StockReservationException("proformaId requerido para reservar stock.");
        }

        List<ReservationNeed> needs = findReservationNeeds(proformaId);
        // Share the product mutex with sales. Never mutate reservations before taking all stock locks.
        Set<Long> productIds = new TreeSet<>(jdbcClient.sql("""
                SELECT DISTINCT product_id FROM product_stock_reservation
                 WHERE proforma_id = ? AND status = 'ACTIVE'
                """).param(proformaId).query(Long.class).list());
        needs.forEach(need -> productIds.add(need.productId()));
        Map<Long, BigDecimal> onHandByProduct = new HashMap<>();
        for (Long productId : productIds) onHandByProduct.put(productId, lockOnHand(productId));

        releaseActiveForProforma(proformaId);
        for (ReservationNeed need : needs) {
            BigDecimal onHand = onHandByProduct.get(need.productId());
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
    public void prepareStockForSale(Map<Long, BigDecimal> quantitiesByProduct, Long sourceProformaId,
                                    List<StockReservationReassignment> reassignments, Long saleId, Long actorId) {
        Map<Long, BigDecimal> selected = StockReservationAllocationPolicy.requestedQuantities(reassignments);
        if (!selected.isEmpty()) {
            requireReassignmentAuditTable();
        }
        Map<Long, BigDecimal> onHand = new HashMap<>();
        for (Long productId : new TreeSet<>(quantitiesByProduct.keySet())) {
            onHand.put(productId, lockOnHand(productId));
        }

        // Existing proforma edits lock their header before replacing/deleting items. NOWAIT avoids
        // cycles with those edits or two source proformas attempting to take each other's stock.
        if (!selected.isEmpty()) {
            List<Long> donorIds = jdbcClient.sql("""
                    SELECT DISTINCT proforma_id FROM product_stock_reservation
                     WHERE id IN (:ids) ORDER BY proforma_id
                    """).param("ids", selected.keySet()).query(Long.class).list();
            for (Long donorId : donorIds) {
                if (Objects.equals(donorId, sourceProformaId)) {
                    throw new StockReservationException("No se puede reasignar la reserva de la propia proforma.");
                }
                try {
                    String status = jdbcClient.sql("SELECT status FROM proforma WHERE id = ? FOR UPDATE NOWAIT")
                            .param(donorId).query(String.class).optional().orElse("");
                    if (!"PENDIENTE".equals(status)) {
                        throw new StockReservationException("La proforma que reservó el stock ya no está PENDIENTE. Actualice las reservas.");
                    }
                } catch (DataAccessException exception) {
                    if (isLockNotAvailable(exception)) {
                        throw new StockReservationException("Otra operación está utilizando la proforma que reservó el stock. Actualice las reservas e intente nuevamente.");
                    }
                    throw exception;
                }
            }
            jdbcClient.sql("""
                    SELECT id FROM product_stock_reservation
                     WHERE id IN (:ids) ORDER BY id FOR UPDATE
                    """).param("ids", selected.keySet()).query(Long.class).list();
        }

        List<ReservationState> activeReservations = quantitiesByProduct.isEmpty() ? List.of() : jdbcClient.sql("""
                SELECT r.id, r.proforma_id, r.product_id, r.quantity,
                       (COALESCE(p.affects_stock, TRUE) AND NOT COALESCE(p.manage_by_serial, FALSE)) AS transferable
                  FROM product_stock_reservation r
                  JOIN product p ON p.id = r.product_id
                 WHERE r.product_id IN (:productIds)
                   AND r.status = 'ACTIVE' AND r.reserved_date = CURRENT_DATE
                 ORDER BY r.id
                """).param("productIds", quantitiesByProduct.keySet())
                .query((rs, rowNum) -> new ReservationState(rs.getLong("id"), rs.getLong("proforma_id"),
                        rs.getLong("product_id"), rs.getBigDecimal("quantity"), rs.getBoolean("transferable")))
                .list();

        List<Allocation> allocations = StockReservationAllocationPolicy.allocate(
                quantitiesByProduct, onHand, activeReservations, sourceProformaId, selected);
        for (Allocation allocation : allocations) {
            ReservationState reservation = allocation.reservation();
            BigDecimal after = reservation.quantity().subtract(allocation.quantity());
            // Preserve a positive original quantity for released reservations (legacy quantity > 0 constraint).
            // The audit retains before/after values; active partial reservations retain only their balance.
            jdbcClient.sql("""
                    UPDATE product_stock_reservation
                       SET quantity = CASE WHEN ? = 0 THEN quantity ELSE ? END,
                           status = CASE WHEN ? = 0 THEN 'RELEASED' ELSE 'ACTIVE' END,
                           released_at = CASE WHEN ? = 0 THEN NOW() ELSE released_at END,
                           updated_at = NOW()
                     WHERE id = ?
                    """).params(after, after, after, after, reservation.id()).update();
            jdbcClient.sql("""
                    INSERT INTO product_stock_reservation_reassignment(
                        sale_id, source_proforma_id, donor_proforma_id, reservation_id, product_id,
                        quantity, quantity_before, quantity_after, reassigned_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                    """).params(saleId, sourceProformaId, reservation.proformaId(), reservation.id(),
                    reservation.productId(), allocation.quantity(), reservation.quantity(), after, actorId).update();
        }
        consumeActiveForProforma(sourceProformaId);
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

    private void requireReassignmentAuditTable() {
        boolean installed = jdbcClient.sql("""
                SELECT to_regclass('product_stock_reservation_reassignment') IS NOT NULL
                """).query(Boolean.class).single();
        if (!installed) {
            throw new StockReservationException(
                    "No se puede utilizar stock reservado porque falta completar una actualización del sistema. "
                            + "Contacta al administrador y vuelve a intentar cuando esté aplicada.");
        }
    }

    private boolean isLockNotAvailable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && "55P03".equals(sqlException.getSQLState())) return true;
        }
        return false;
    }

    private record ReservationNeed(Long productId, String sku, BigDecimal quantity) {
    }
}
