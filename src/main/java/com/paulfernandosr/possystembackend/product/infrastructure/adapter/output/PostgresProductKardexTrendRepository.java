package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.*;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductKardexTrendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProductKardexTrendRepository implements ProductKardexTrendRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ProductKardexTrendProductResponse> findProductById(Long productId) {
        String sql = """
                SELECT
                  p.id AS product_id,
                  p.sku,
                  p.name AS product_name,
                  p.category,
                  p.brand,
                  p.model,
                  p.presentation,
                  p.manage_by_serial
                FROM product p
                WHERE p.id = ?
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new ProductKardexTrendProductRowMapper())
                .optional();
    }

    @Override
    public Optional<ProductKardexTrendStockSnapshot> findLastStockSnapshotBefore(Long productId, LocalDateTime before) {
        String sql = """
                SELECT
                  m.balance_qty AS stock,
                  m.balance_cost AS average_cost
                FROM product_stock_movement m
                WHERE m.product_id = ?
                  AND m.created_at < ?
                  AND m.balance_qty IS NOT NULL
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 1
                """;

        return jdbcClient.sql(sql)
                .params(productId, before)
                .query((rs, rowNum) -> new ProductKardexTrendStockSnapshot(
                        rs.getBigDecimal("stock"),
                        rs.getBigDecimal("average_cost")
                ))
                .optional();
    }

    @Override
    public List<ProductKardexTrendMovement> findMovements(ProductKardexTrendFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                  m.id,
                  m.created_at AS movement_date,
                  m.movement_type,
                  m.source_table,
                  COALESCE(m.quantity_in, 0) AS quantity_in,
                  COALESCE(m.quantity_out, 0) AS quantity_out,
                  CASE
                    WHEN m.balance_qty IS NULL THEN NULL
                    ELSE m.balance_qty - COALESCE(m.quantity_in, 0) + COALESCE(m.quantity_out, 0)
                  END AS stock_before,
                  m.balance_qty AS stock_after,
                  m.unit_cost,
                  m.total_cost,
                  m.balance_cost AS average_cost_after,
                  CASE
                    WHEN m.source_table = 'purchase_item' THEN pi.total_cost
                    WHEN m.source_table = 'sale_item' THEN si.revenue_total
                    WHEN m.source_table = 'counter_sale_item' THEN csi.revenue_total
                    WHEN m.source_table = 'product_stock_adjustment' THEN psa.total_cost
                    ELSE NULL
                  END AS source_line_total
                FROM product_stock_movement m
                LEFT JOIN purchase_item pi
                       ON m.source_table = 'purchase_item'
                      AND m.source_id = pi.id
                LEFT JOIN sale_item si
                       ON m.source_table = 'sale_item'
                      AND m.source_id = si.id
                LEFT JOIN counter_sale_item csi
                       ON m.source_table = 'counter_sale_item'
                      AND m.source_id = csi.id
                LEFT JOIN product_stock_adjustment psa
                       ON m.source_table = 'product_stock_adjustment'
                      AND m.source_id = psa.id
                WHERE m.product_id = ?
                  AND m.created_at >= ?
                  AND m.created_at < ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(filter.productId());
        params.add(filter.dateFrom().atStartOfDay());
        params.add(filter.dateTo().plusDays(1).atStartOfDay());

        if (filter.source() != null && filter.source() != KardexSource.ALL) {
            sql.append("AND m.source_table = ?\n");
            params.add(sourceTable(filter.source()));
        }

        if (filter.movementType() != null && !filter.movementType().isBlank()) {
            sql.append("AND m.movement_type = ?\n");
            params.add(filter.movementType());
        }

        sql.append("ORDER BY m.created_at ASC, m.id ASC\n");

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(new ProductKardexTrendMovementRowMapper())
                .list();
    }

    private String sourceTable(KardexSource source) {
        return switch (source) {
            case PURCHASE -> "purchase_item";
            case SALE -> "sale_item";
            case COUNTER_SALE -> "counter_sale_item";
            case ADJUSTMENT -> "product_stock_adjustment";
            case ALL -> null;
        };
    }

    private static final class ProductKardexTrendProductRowMapper implements RowMapper<ProductKardexTrendProductResponse> {
        @Override
        public ProductKardexTrendProductResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProductKardexTrendProductResponse(
                    rs.getLong("product_id"),
                    rs.getString("sku"),
                    rs.getString("product_name"),
                    rs.getString("category"),
                    rs.getString("brand"),
                    rs.getString("model"),
                    rs.getString("presentation"),
                    rs.getObject("manage_by_serial", Boolean.class)
            );
        }
    }

    private static final class ProductKardexTrendMovementRowMapper implements RowMapper<ProductKardexTrendMovement> {
        @Override
        public ProductKardexTrendMovement mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp movementDate = rs.getTimestamp("movement_date");
            BigDecimal quantityIn = rs.getBigDecimal("quantity_in");
            BigDecimal quantityOut = rs.getBigDecimal("quantity_out");

            return new ProductKardexTrendMovement(
                    rs.getLong("id"),
                    movementDate != null ? movementDate.toLocalDateTime() : null,
                    rs.getString("movement_type"),
                    rs.getString("source_table"),
                    quantityIn,
                    quantityOut,
                    rs.getBigDecimal("stock_before"),
                    rs.getBigDecimal("stock_after"),
                    rs.getBigDecimal("unit_cost"),
                    rs.getBigDecimal("total_cost"),
                    rs.getBigDecimal("average_cost_after"),
                    rs.getBigDecimal("source_line_total")
            );
        }
    }
}
