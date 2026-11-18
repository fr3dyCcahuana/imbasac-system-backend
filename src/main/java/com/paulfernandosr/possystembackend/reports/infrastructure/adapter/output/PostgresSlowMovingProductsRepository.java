package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.SlowMovingProduct;
import com.paulfernandosr.possystembackend.reports.domain.port.output.SlowMovingProductsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresSlowMovingProductsRepository implements SlowMovingProductsRepository {
    private final JdbcClient jdbc;

    @Override
    public List<String> findModels() {
        return jdbc.sql("""
                SELECT DISTINCT TRIM(model) AS model FROM product
                WHERE affects_stock = TRUE AND NULLIF(TRIM(model), '') IS NOT NULL
                ORDER BY model
                """).query(String.class).list();
    }

    @Override
    public List<SlowMovingProduct> find(SlowMovingFilter filter, Integer limit) {
        // Work from the catalog: a product without any commercial document must remain visible.
        // affects_stock on sale_item is a snapshot. Composed SUNAT lines already sold at the
        // counter have it set to false, so the fiscal document cannot count the same exit twice.
        String sql = """
                WITH catalog_stock AS (
                  SELECT p.id, p.sku, p.name, COALESCE(TRIM(p.model), '') AS model,
                    p.price_a, p.price_b, p.price_c, p.price_d,
                    CASE WHEN p.manage_by_serial THEN
                      (SELECT COUNT(*)::numeric FROM product_serial_unit su
                       WHERE su.product_id = p.id AND su.status = 'EN_ALMACEN')
                    ELSE COALESCE(ps.quantity_on_hand, 0) END AS stock
                  FROM product p
                  LEFT JOIN product_stock ps ON ps.product_id = p.id
                  WHERE p.affects_stock = TRUE
                    AND (:model = '' OR LOWER(TRIM(p.model)) = LOWER(:model))
                    AND p.created_at < CAST(:to AS date) + INTERVAL '1 day'
                ), catalog AS (
                  SELECT * FROM catalog_stock WHERE (:stockOnly = FALSE OR stock > 0)
                ), sales AS (
                  SELECT csi.product_id, 'COUNTER_SALE-' || cs.id AS operation,
                    cs.issue_date AS sale_date, csi.quantity,
                    CASE WHEN cs.currency = 'USD' THEN csi.revenue_total * NULLIF(cs.exchange_rate, 0)
                         ELSE csi.revenue_total END AS revenue_pen
                  FROM counter_sale cs
                  JOIN counter_sale_item csi ON csi.counter_sale_id = cs.id
                  JOIN catalog p ON p.id = csi.product_id
                  WHERE cs.status = 'EMITIDA' AND csi.line_kind = 'VENDIDO'
                    AND csi.affects_stock = TRUE AND cs.issue_date <= :to
                  UNION ALL
                  SELECT si.product_id, 'SALE-' || s.id, s.issue_date, si.quantity,
                    CASE WHEN s.currency = 'USD' THEN si.revenue_total * NULLIF(s.exchange_rate, 0)
                         ELSE si.revenue_total END
                  FROM sale s
                  JOIN sale_item si ON si.sale_id = s.id
                  JOIN catalog p ON p.id = si.product_id
                  WHERE s.status = 'EMITIDA' AND si.line_kind = 'VENDIDO'
                    AND si.affects_stock = TRUE AND s.issue_date <= :to
                  UNION ALL
                  -- Non-fiscal proforma lines leave stock at conversion and are absent from sale_item.
                  SELECT pi.product_id, 'SALE-' || s.id, s.issue_date, pi.quantity,
                    CASE WHEN s.currency = 'USD' THEN pi.line_subtotal * NULLIF(s.exchange_rate, 0)
                         ELSE pi.line_subtotal END
                  FROM proforma pf
                  JOIN sale s ON s.id = pf.converted_sale_id
                  JOIN proforma_item pi ON pi.proforma_id = pf.id
                  JOIN catalog p ON p.id = pi.product_id
                  WHERE pf.status = 'CONVERTIDA' AND s.status = 'EMITIDA'
                    AND pi.facturable_sunat = FALSE AND pi.affects_stock = TRUE
                    AND s.issue_date <= :to
                ), sales_summary AS (
                  SELECT product_id,
                    COUNT(DISTINCT operation) AS historical_sales,
                    MAX(sale_date) AS last_sale_date,
                    COUNT(DISTINCT operation) FILTER (WHERE sale_date >= :from) AS count_sales,
                    COALESCE(SUM(quantity) FILTER (WHERE sale_date >= :from), 0) AS sold_qty,
                    CASE WHEN COUNT(*) FILTER (WHERE sale_date >= :from AND revenue_pen IS NULL) = 0
                      THEN SUM(revenue_pen) FILTER (WHERE sale_date >= :from)
                        / NULLIF(SUM(quantity) FILTER (WHERE sale_date >= :from), 0)
                      ELSE NULL END AS average_sale_price
                  FROM sales WHERE quantity > 0 GROUP BY product_id
                ), returns AS (
                  SELECT cni.product_id, SUM(cni.quantity) AS returned_qty
                  FROM credit_note cn
                  JOIN credit_note_item cni ON cni.credit_note_id = cn.id
                  JOIN sale s ON s.id = cn.sale_id
                  JOIN catalog p ON p.id = cni.product_id
                  WHERE cn.status <> 'ANULADA' AND s.status = 'EMITIDA'
                    AND cni.returned_to_stock = TRUE
                    AND cn.issue_date BETWEEN :from AND :to
                  GROUP BY cni.product_id
                ), first_stock AS (
                  SELECT m.product_id, MIN(m.created_at::date) AS first_stock_date
                  FROM product_stock_movement m JOIN catalog p ON p.id = m.product_id
                  WHERE m.quantity_in > 0
                    AND m.movement_type IN ('IN_PURCHASE', 'IN_ADJUST')
                    AND m.created_at < CAST(:to AS date) + INTERVAL '1 day'
                  GROUP BY m.product_id
                ), report AS (
                  SELECT p.*, COALESCE(ss.historical_sales, 0) AS historical_sales,
                    COALESCE(ss.count_sales, 0) AS count_sales,
                    COALESCE(ss.sold_qty, 0) AS sold_qty,
                    COALESCE(r.returned_qty, 0) AS returned_qty,
                    COALESCE(ss.sold_qty, 0) - COALESCE(r.returned_qty, 0) AS net_qty,
                    ss.last_sale_date, ss.average_sale_price, fs.first_stock_date,
                    CAST(:to AS date) - ss.last_sale_date AS days_without_sale,
                    CAST(:to AS date) - fs.first_stock_date AS days_since_first_stock
                  FROM catalog p
                  LEFT JOIN sales_summary ss ON ss.product_id = p.id
                  LEFT JOIN returns r ON r.product_id = p.id
                  LEFT JOIN first_stock fs ON fs.product_id = p.id
                )
                SELECT * FROM report
                WHERE (:stockOnly = FALSE OR stock > 0)
                  AND (:status = 'ALL'
                    OR (:status = 'NO_SALES' AND count_sales = 0)
                    OR (:status = 'NEVER_SOLD' AND historical_sales = 0))
                ORDER BY %s ASC, count_sales ASC, sold_qty ASC,
                  COALESCE(last_sale_date, first_stock_date) ASC NULLS LAST,
                  stock DESC, name ASC, id ASC
                """.formatted(filter.sort() == SlowMovingFilter.Sort.QTY ? "net_qty" : "count_sales");
        if (limit != null) sql += " LIMIT :limit";
        var statement = jdbc.sql(sql)
                .param("from", filter.from()).param("to", filter.to())
                .param("model", filter.model()).param("status", filter.status().name())
                .param("stockOnly", filter.stockOnly());
        if (limit != null) statement.param("limit", limit);
        return statement.query((rs, row) -> {
            LocalDate firstStock = rs.getObject("first_stock_date", LocalDate.class);
            return new SlowMovingProduct(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    rs.getString("model"), rs.getBigDecimal("stock"), rs.getLong("count_sales"),
                    rs.getBigDecimal("sold_qty"), rs.getBigDecimal("returned_qty"), rs.getBigDecimal("net_qty"),
                    rs.getLong("historical_sales"), rs.getObject("last_sale_date", LocalDate.class),
                    rs.getObject("days_without_sale", Integer.class), firstStock,
                    rs.getObject("days_since_first_stock", Integer.class),
                    firstStock != null && !firstStock.isBefore(filter.from()),
                    rs.getBigDecimal("price_a"), rs.getBigDecimal("price_b"), rs.getBigDecimal("price_c"),
                    rs.getBigDecimal("price_d"), rs.getBigDecimal("average_sale_price"));
        }).list();
    }
}
