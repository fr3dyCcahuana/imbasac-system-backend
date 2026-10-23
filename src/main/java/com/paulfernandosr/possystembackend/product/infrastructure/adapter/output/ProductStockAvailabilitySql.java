package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

/** Shared projection used by the catalog and the sale stock check. The first bind is the source proforma number. */
final class ProductStockAvailabilitySql {
    private ProductStockAvailabilitySql() {
    }

    static final String CTE = """
            WITH source_proforma AS (
              SELECT (
                SELECT id FROM proforma
                 WHERE number = CAST(? AS BIGINT)
                 ORDER BY id DESC
                 LIMIT 1
              ) AS id
            ), reservation_totals AS (
              SELECT r.product_id,
                     COALESCE(SUM(r.quantity) FILTER (WHERE r.proforma_id IS DISTINCT FROM source.id), 0) AS reserved_qty,
                     COALESCE(SUM(r.quantity) FILTER (WHERE r.proforma_id = source.id), 0) AS source_reserved_qty
                FROM product_stock_reservation r
                CROSS JOIN source_proforma source
               WHERE r.status = 'ACTIVE'
                 AND r.reserved_date = CURRENT_DATE
               GROUP BY r.product_id
            ), available_serial_stock AS (
              SELECT product_id, COUNT(*) AS quantity_on_hand
                FROM product_serial_unit
               WHERE status = 'EN_ALMACEN'
               GROUP BY product_id
            ), base AS (
            """;

    static final String STOCK_COLUMNS = """
            CASE WHEN p.manage_by_serial = TRUE
                   AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                 THEN COALESCE(serial_stock.quantity_on_hand, 0)
                 ELSE COALESCE(ps.quantity_on_hand, 0)
            END AS stock_on_hand,
            CASE WHEN p.manage_by_serial = TRUE
                   AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                 THEN 0 ELSE COALESCE(res.reserved_qty, 0)
            END AS stock_reserved,
            CASE WHEN p.manage_by_serial = TRUE
                   AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                 THEN 0 ELSE COALESCE(res.source_reserved_qty, 0)
            END AS stock_reserved_by_source,
            CASE WHEN p.manage_by_serial = TRUE
                   AND UPPER(COALESCE(p.category, '')) IN ('MOTOR','MOTOCICLETAS')
                 THEN COALESCE(serial_stock.quantity_on_hand, 0)
                 ELSE GREATEST(COALESCE(ps.quantity_on_hand, 0) - COALESCE(res.reserved_qty, 0), 0)
            END AS stock_available
            """;

    static final String JOINS = """
            LEFT JOIN product_stock ps ON ps.product_id = p.id
            LEFT JOIN reservation_totals res ON res.product_id = p.id
            LEFT JOIN available_serial_stock serial_stock ON serial_stock.product_id = p.id
            """;

    // A sale can claim an existing reservation after confirmation. Proformas can only reserve free stock.
    static final String STOCK_FILTER = "(? = FALSE OR affects_stock = FALSE OR CASE WHEN ? = 'SALE' THEN stock_on_hand ELSE stock_available END > 0)";
}
