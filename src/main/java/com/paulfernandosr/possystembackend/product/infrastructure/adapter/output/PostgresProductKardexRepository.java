package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexEntry;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductKardexRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductKardexEntryRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresProductKardexRepository implements ProductKardexRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Page<ProductKardexEntry> findPage(
            String query,
            Long productId,
            String category,
            String brand,
            String model,
            String movementType,
            String direction,
            String source,
            String docType,
            String series,
            String number,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        SqlWhere movementWhere = buildMovementWhere(productId, movementType, direction, source, dateFrom, dateTo);
        String baseSql = baseSql(movementWhere.sql());
        SqlWhere where = buildWhere(
                query,
                null,
                category,
                brand,
                model,
                null,
                "ALL",
                "ALL",
                docType,
                series,
                number,
                null,
                null
        );
        List<Object> filteredParams = new ArrayList<>(movementWhere.params());
        filteredParams.addAll(where.params());

        String countSql = baseSql + "\nSELECT COUNT(1) FROM enriched\n" + where.sql();
        long totalElements = jdbcClient.sql(countSql)
                .params(filteredParams.toArray())
                .query(Long.class)
                .single();

        int size = pageable.getSize();
        int page = pageable.getNumber();

        List<Object> selectParams = new ArrayList<>(filteredParams);
        selectParams.add(size);
        selectParams.add(page * size);

        String selectSql = baseSql + """
                SELECT
                  id,
                  movement_date,
                  product_id,
                  sku,
                  product_name,
                  category,
                  brand,
                  model,
                  presentation,
                  manage_by_serial,
                  existence_type_code,
                  movement_type,
                  movement_label,
                  direction,
                  source_table,
                  source_id,
                  source_document_type,
                  source_series,
                  source_number,
                  source_issue_date,
                  source_status,
                  source_line_number,
                  counterpart_type,
                  counterpart_document_number,
                  counterpart_name,
                  quantity_in,
                  quantity_out,
                  movement_quantity,
                  stock_before,
                  stock_after,
                  unit_cost,
                  total_cost,
                  average_cost_after,
                  source_unit_price,
                  source_line_total,
                  adjustment_reason,
                  note
                FROM enriched
                """ + where.sql() + """
                ORDER BY movement_date DESC, id DESC
                LIMIT ?
                OFFSET ?
                """;

        List<ProductKardexEntry> content = jdbcClient.sql(selectSql)
                .params(selectParams.toArray())
                .query(new ProductKardexEntryRowMapper())
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(size), 0, RoundingMode.CEILING);

        return Page.<ProductKardexEntry>builder()
                .content(content)
                .number(page)
                .size(size)
                .numberOfElements(content.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public List<ProductKardexEntry> findInventoryReportProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        String placeholders = placeholders(productIds.size());
        String orderBy = orderByProductIds(productIds);
        String sql = """
                SELECT
                  p.id AS id,
                  NULL::timestamp AS movement_date,
                  p.id AS product_id,
                  p.sku,
                  p.name AS product_name,
                  p.category,
                  p.brand,
                  p.model,
                  p.presentation,
                  p.manage_by_serial,
                  p.existence_type_code,
                  NULL::varchar AS movement_type,
                  NULL::varchar AS movement_label,
                  NULL::varchar AS direction,
                  NULL::varchar AS source_table,
                  NULL::bigint AS source_id,
                  NULL::varchar AS source_document_type,
                  NULL::varchar AS source_series,
                  NULL::varchar AS source_number,
                  NULL::date AS source_issue_date,
                  NULL::varchar AS source_status,
                  NULL::integer AS source_line_number,
                  NULL::varchar AS counterpart_type,
                  NULL::varchar AS counterpart_document_number,
                  NULL::varchar AS counterpart_name,
                  0::numeric AS quantity_in,
                  0::numeric AS quantity_out,
                  0::numeric AS movement_quantity,
                  NULL::numeric AS stock_before,
                  NULL::numeric AS stock_after,
                  NULL::numeric AS unit_cost,
                  NULL::numeric AS total_cost,
                  NULL::numeric AS average_cost_after,
                  NULL::numeric AS source_unit_price,
                  NULL::numeric AS source_line_total,
                  NULL::varchar AS adjustment_reason,
                  NULL::varchar AS note
                FROM product p
                WHERE p.id IN (%s)
                ORDER BY %s
                """.formatted(placeholders, orderBy);

        return jdbcClient.sql(sql)
                .params(productIds.toArray())
                .query(new ProductKardexEntryRowMapper())
                .list();
    }

    @Override
    public List<ProductKardexEntry> findInventoryReportProductsWithMovements(LocalDate dateFrom, LocalDate dateTo) {
        String baseSql = baseSql("WHERE m.created_at >= ? AND m.created_at < ?");
        String sql = baseSql + """
                , product_rows AS (
                    SELECT DISTINCT
                      product_id,
                      sku,
                      product_name,
                      category,
                      brand,
                      model,
                      presentation,
                      manage_by_serial,
                      existence_type_code
                    FROM enriched
                    WHERE tax_export_eligible = TRUE
                )
                SELECT
                  product_id AS id,
                  NULL::timestamp AS movement_date,
                  product_id,
                  sku,
                  product_name,
                  category,
                  brand,
                  model,
                  presentation,
                  manage_by_serial,
                  existence_type_code,
                  NULL::varchar AS movement_type,
                  NULL::varchar AS movement_label,
                  NULL::varchar AS direction,
                  NULL::varchar AS source_table,
                  NULL::bigint AS source_id,
                  NULL::varchar AS source_document_type,
                  NULL::varchar AS source_series,
                  NULL::varchar AS source_number,
                  NULL::date AS source_issue_date,
                  NULL::varchar AS source_status,
                  NULL::integer AS source_line_number,
                  NULL::varchar AS counterpart_type,
                  NULL::varchar AS counterpart_document_number,
                  NULL::varchar AS counterpart_name,
                  0::numeric AS quantity_in,
                  0::numeric AS quantity_out,
                  0::numeric AS movement_quantity,
                  NULL::numeric AS stock_before,
                  NULL::numeric AS stock_after,
                  NULL::numeric AS unit_cost,
                  NULL::numeric AS total_cost,
                  NULL::numeric AS average_cost_after,
                  NULL::numeric AS source_unit_price,
                  NULL::numeric AS source_line_total,
                  NULL::varchar AS adjustment_reason,
                  NULL::varchar AS note
                FROM product_rows
                ORDER BY sku ASC, product_id ASC
                """;

        LocalDate safeFrom = dateFrom != null ? dateFrom : LocalDate.of(1900, 1, 1);
        LocalDate safeTo = dateTo != null ? dateTo : LocalDate.now();

        return jdbcClient.sql(sql)
                .params(safeFrom.atStartOfDay(), safeTo.plusDays(1).atStartOfDay())
                .query(new ProductKardexEntryRowMapper())
                .list();
    }

    @Override
    public List<ProductKardexEntry> findInventoryReportMovementsForAllProducts(
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        LocalDate safeFrom = dateFrom != null ? dateFrom : LocalDate.of(1900, 1, 1);
        LocalDate safeTo = dateTo != null ? dateTo : LocalDate.now();

        return jdbcClient.sql(inventoryReportMovementsSql("", "sku ASC, product_id ASC, movement_date ASC, id ASC"))
                .params(safeFrom.atStartOfDay(), safeTo.plusDays(1).atStartOfDay())
                .query(new ProductKardexEntryRowMapper())
                .list();
    }

    @Override
    public List<ProductKardexEntry> findInventoryReportMovements(
            List<Long> productIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        String placeholders = placeholders(productIds.size());
        LocalDate safeFrom = dateFrom != null ? dateFrom : LocalDate.of(1900, 1, 1);
        LocalDate safeTo = dateTo != null ? dateTo : LocalDate.now();
        List<Object> params = new ArrayList<>();
        params.add(safeFrom.atStartOfDay());
        params.add(safeTo.plusDays(1).atStartOfDay());
        params.addAll(productIds);

        return jdbcClient.sql(inventoryReportMovementsSql(
                        "AND m.product_id IN (%s)".formatted(placeholders),
                        "product_id ASC, movement_date ASC, id ASC"
                ))
                .params(params.toArray())
                .query(new ProductKardexEntryRowMapper())
                .list();
    }

    private String inventoryReportMovementsSql(String productFilterSql, String orderBySql) {
        String safeProductFilterSql = productFilterSql == null || productFilterSql.isBlank() ? "" : "\n      " + productFilterSql;
        String safeOrderBySql = orderBySql == null || orderBySql.isBlank()
                ? "product_id ASC, movement_date ASC, id ASC"
                : orderBySql;

        return """
                WITH filtered_movements AS (
                  SELECT
                    m.id,
                    m.created_at AS movement_date,
                    m.product_id,
                    p.sku,
                    p.name AS product_name,
                    p.category,
                    p.brand,
                    p.model,
                    p.presentation,
                    p.manage_by_serial,
                    p.existence_type_code,
                    m.movement_type,
                    CASE
                      WHEN COALESCE(m.quantity_in, 0) > 0 THEN 'ENTRADA'
                      WHEN COALESCE(m.quantity_out, 0) > 0 THEN 'SALIDA'
                      ELSE 'NEUTRO'
                    END AS direction,
                    m.source_table,
                    m.source_id,
                    COALESCE(m.quantity_in, 0) AS quantity_in,
                    COALESCE(m.quantity_out, 0) AS quantity_out,
                    CASE
                      WHEN COALESCE(m.quantity_in, 0) > 0 THEN COALESCE(m.quantity_in, 0)
                      WHEN COALESCE(m.quantity_out, 0) > 0 THEN COALESCE(m.quantity_out, 0)
                      ELSE 0
                    END AS movement_quantity,
                    CASE
                      WHEN m.balance_qty IS NULL THEN NULL
                      ELSE m.balance_qty - COALESCE(m.quantity_in, 0) + COALESCE(m.quantity_out, 0)
                    END AS stock_before,
                    m.balance_qty AS stock_after,
                    m.unit_cost,
                    m.total_cost,
                    m.balance_cost AS average_cost_after
                  FROM product_stock_movement m
                  INNER JOIN product p
                          ON p.id = m.product_id
                 WHERE m.created_at >= ?
                   AND m.created_at < ?
                   AND m.source_table IN ('purchase_item', 'sale_item', 'counter_sale_item', 'credit_note_item')%s
                ),
                counter_movements AS (
                  SELECT DISTINCT source_id
                    FROM filtered_movements
                   WHERE source_table = 'counter_sale_item'
                     AND source_id IS NOT NULL
                ),
                latest_link AS (
                  SELECT DISTINCT ON (li.counter_sale_item_id)
                         li.counter_sale_item_id,
                         li.emitted_unit_price,
                         li.emitted_revenue_total,
                         l.emitted_doc_type,
                         l.emitted_series,
                         l.emitted_number,
                         COALESCE(sl.issue_date, l.associated_at::date) AS issue_date
                    FROM sale_counter_sale_sunat_link_item li
                    JOIN counter_movements cm
                      ON cm.source_id = li.counter_sale_item_id
                    JOIN sale_counter_sale_sunat_link l
                      ON l.sale_id = li.sale_id
                     AND l.counter_sale_id = li.counter_sale_id
                    LEFT JOIN sale sl
                      ON sl.id = l.sale_id
                   WHERE l.reservation_status = 'ACEPTADO'
                   ORDER BY li.counter_sale_item_id,
                            COALESCE(l.associated_at, l.updated_at, l.reserved_at) DESC NULLS LAST,
                            li.id DESC
                ),
                latest_combo AS (
                  SELECT DISTINCT ON (cl.counter_sale_item_id)
                         cl.counter_sale_item_id,
                         cl.emitted_unit_price,
                         cl.emitted_revenue_total,
                         c.emitted_doc_type,
                         c.emitted_series,
                         c.emitted_number,
                         COALESCE(sg.issue_date, c.issue_date, c.associated_at::date) AS issue_date
                    FROM counter_sale_sunat_combo_line cl
                    JOIN counter_movements cm
                      ON cm.source_id = cl.counter_sale_item_id
                    JOIN counter_sale_sunat_combo c
                      ON c.id = cl.combo_id
                    LEFT JOIN sale sg
                      ON sg.id = c.generated_sale_id
                   WHERE c.combo_status = 'ACEPTADO'
                   ORDER BY cl.counter_sale_item_id,
                            COALESCE(c.associated_at, c.updated_at, c.created_at) DESC NULLS LAST,
                            cl.id DESC
                ),
                enriched AS (
                  SELECT
                    fm.id,
                    fm.movement_date,
                    fm.product_id,
                    fm.sku,
                    fm.product_name,
                    fm.category,
                    fm.brand,
                    fm.model,
                    fm.presentation,
                    fm.manage_by_serial,
                    fm.existence_type_code,
                    fm.movement_type,
                    'COMPRA'::varchar AS movement_label,
                    fm.direction,
                    fm.source_table,
                    fm.source_id,
                    pu.document_type AS source_document_type,
                    pu.document_series AS source_series,
                    pu.document_number AS source_number,
                    COALESCE(pu.entry_date, pu.issue_date) AS source_issue_date,
                    pu.status AS source_status,
                    pi.line_number AS source_line_number,
                    'PROVEEDOR'::varchar AS counterpart_type,
                    pu.supplier_ruc AS counterpart_document_number,
                    pu.supplier_business_name AS counterpart_name,
                    fm.quantity_in,
                    fm.quantity_out,
                    fm.movement_quantity,
                    fm.stock_before,
                    fm.stock_after,
                    fm.unit_cost,
                    fm.total_cost,
                    fm.average_cost_after,
                    pi.unit_cost AS source_unit_price,
                    pi.total_cost AS source_line_total,
                    NULL::varchar AS adjustment_reason,
                    pu.notes AS note
                  FROM filtered_movements fm
                  JOIN purchase_item pi
                    ON pi.id = fm.source_id
                  JOIN purchase pu
                    ON pu.id = pi.purchase_id
                 WHERE fm.source_table = 'purchase_item'
                   AND UPPER(COALESCE(pu.document_type, '')) IN ('FACTURA', 'BOLETA')

                 UNION ALL

                  SELECT
                    fm.id,
                    fm.movement_date,
                    fm.product_id,
                    fm.sku,
                    fm.product_name,
                    fm.category,
                    fm.brand,
                    fm.model,
                    fm.presentation,
                    fm.manage_by_serial,
                    fm.existence_type_code,
                    fm.movement_type,
                    'VENTA'::varchar AS movement_label,
                    fm.direction,
                    fm.source_table,
                    fm.source_id,
                    s.doc_type AS source_document_type,
                    s.series AS source_series,
                    s.number::text AS source_number,
                    s.issue_date AS source_issue_date,
                    s.status AS source_status,
                    si.line_number AS source_line_number,
                    'CLIENTE'::varchar AS counterpart_type,
                    s.customer_doc_number AS counterpart_document_number,
                    s.customer_name AS counterpart_name,
                    fm.quantity_in,
                    fm.quantity_out,
                    fm.movement_quantity,
                    fm.stock_before,
                    fm.stock_after,
                    fm.unit_cost,
                    fm.total_cost,
                    fm.average_cost_after,
                    si.unit_price AS source_unit_price,
                    si.revenue_total AS source_line_total,
                    NULL::varchar AS adjustment_reason,
                    s.notes AS note
                  FROM filtered_movements fm
                  JOIN sale_item si
                    ON si.id = fm.source_id
                  JOIN sale s
                    ON s.id = si.sale_id
                 WHERE fm.source_table = 'sale_item'
                   AND fm.quantity_out > 0
                   AND UPPER(COALESCE(s.doc_type, '')) IN ('FACTURA', 'BOLETA')
                   AND UPPER(COALESCE(s.status, '')) = 'EMITIDA'

                 UNION ALL

                  SELECT
                    fm.id,
                    fm.movement_date,
                    fm.product_id,
                    fm.sku,
                    fm.product_name,
                    fm.category,
                    fm.brand,
                    fm.model,
                    fm.presentation,
                    fm.manage_by_serial,
                    fm.existence_type_code,
                    fm.movement_type,
                    'VENTANILLA'::varchar AS movement_label,
                    fm.direction,
                    fm.source_table,
                    fm.source_id,
                    COALESCE(NULLIF(cs.associated_doc_type, ''), ll.emitted_doc_type, lc.emitted_doc_type, cs_sunat.doc_type, 'VENTANILLA') AS source_document_type,
                    COALESCE(NULLIF(cs.associated_series, ''), ll.emitted_series, lc.emitted_series, cs_sunat.series, cs.series) AS source_series,
                    COALESCE(cs.associated_number::text, ll.emitted_number::text, lc.emitted_number::text, cs_sunat.number::text, cs.number::text) AS source_number,
                    COALESCE(cs_sunat.issue_date, ll.issue_date, lc.issue_date, cs.associated_at::date, cs.issue_date) AS source_issue_date,
                    cs.status AS source_status,
                    csi.line_number AS source_line_number,
                    'CLIENTE'::varchar AS counterpart_type,
                    cs.customer_doc_number AS counterpart_document_number,
                    cs.customer_name AS counterpart_name,
                    fm.quantity_in,
                    fm.quantity_out,
                    fm.movement_quantity,
                    fm.stock_before,
                    fm.stock_after,
                    fm.unit_cost,
                    fm.total_cost,
                    fm.average_cost_after,
                    COALESCE(ll.emitted_unit_price, lc.emitted_unit_price, csi.unit_price) AS source_unit_price,
                    COALESCE(ll.emitted_revenue_total, lc.emitted_revenue_total, csi.revenue_total) AS source_line_total,
                    NULL::varchar AS adjustment_reason,
                    cs.notes AS note
                  FROM filtered_movements fm
                  JOIN counter_sale_item csi
                    ON csi.id = fm.source_id
                  JOIN counter_sale cs
                    ON cs.id = csi.counter_sale_id
                  LEFT JOIN sale cs_sunat
                    ON cs_sunat.id = cs.associated_sale_id
                  LEFT JOIN latest_link ll
                    ON ll.counter_sale_item_id = csi.id
                  LEFT JOIN latest_combo lc
                    ON lc.counter_sale_item_id = csi.id
                 WHERE fm.source_table = 'counter_sale_item'
                   AND fm.movement_type = 'OUT_COUNTER_SALE'
                   AND UPPER(COALESCE(cs.associated_doc_type, ll.emitted_doc_type, lc.emitted_doc_type, cs_sunat.doc_type, '')) IN ('FACTURA', 'BOLETA')
                   AND (
                        COALESCE(cs.associated_to_sunat, FALSE) = TRUE
                     OR ll.counter_sale_item_id IS NOT NULL
                     OR lc.counter_sale_item_id IS NOT NULL
                     OR UPPER(COALESCE(cs_sunat.status, '')) = 'EMITIDA'
                   )

                 UNION ALL

                  SELECT
                    fm.id,
                    fm.movement_date,
                    fm.product_id,
                    fm.sku,
                    fm.product_name,
                    fm.category,
                    fm.brand,
                    fm.model,
                    fm.presentation,
                    fm.manage_by_serial,
                    fm.existence_type_code,
                    fm.movement_type,
                    'NOTA DE CREDITO'::varchar AS movement_label,
                    fm.direction,
                    fm.source_table,
                    fm.source_id,
                    'NOTA_CREDITO'::varchar AS source_document_type,
                    cn.series AS source_series,
                    cn.number::text AS source_number,
                    cn.issue_date AS source_issue_date,
                    cn.sunat_status AS source_status,
                    cni.line_number AS source_line_number,
                    'CLIENTE'::varchar AS counterpart_type,
                    cn.customer_doc_number AS counterpart_document_number,
                    cn.customer_name AS counterpart_name,
                    fm.quantity_in,
                    fm.quantity_out,
                    fm.movement_quantity,
                    fm.stock_before,
                    fm.stock_after,
                    fm.unit_cost,
                    fm.total_cost,
                    fm.average_cost_after,
                    cni.unit_price AS source_unit_price,
                    cni.revenue_total AS source_line_total,
                    NULL::varchar AS adjustment_reason,
                    cn.reason AS note
                  FROM filtered_movements fm
                  JOIN credit_note_item cni
                    ON cni.id = fm.source_id
                  JOIN credit_note cn
                    ON cn.id = cni.credit_note_id
                 WHERE fm.source_table = 'credit_note_item'
                   AND fm.movement_type = 'IN_RETURN'
                   AND UPPER(COALESCE(cn.status, '')) = 'EMITIDA'
                )
                SELECT
                  id,
                  movement_date,
                  product_id,
                  sku,
                  product_name,
                  category,
                  brand,
                  model,
                  presentation,
                  manage_by_serial,
                  existence_type_code,
                  movement_type,
                  movement_label,
                  direction,
                  source_table,
                  source_id,
                  source_document_type,
                  source_series,
                  source_number,
                  source_issue_date,
                  source_status,
                  source_line_number,
                  counterpart_type,
                  counterpart_document_number,
                  counterpart_name,
                  quantity_in,
                  quantity_out,
                  movement_quantity,
                  stock_before,
                  stock_after,
                  unit_cost,
                  total_cost,
                  average_cost_after,
                  source_unit_price,
                  source_line_total,
                  adjustment_reason,
                  note
                FROM enriched
                ORDER BY %s
                """.formatted(safeProductFilterSql, safeOrderBySql);
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private String orderByProductIds(List<Long> productIds) {
        StringBuilder sql = new StringBuilder("CASE p.id ");
        for (int i = 0; i < productIds.size(); i++) {
            sql.append("WHEN ").append(productIds.get(i)).append(" THEN ").append(i).append(" ");
        }
        sql.append("ELSE ").append(productIds.size()).append(" END");
        return sql.toString();
    }

    private SqlWhere buildMovementWhere(
            Long productId,
            String movementType,
            String direction,
            String source,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        StringBuilder sql = new StringBuilder("WHERE 1=1\n");
        List<Object> params = new ArrayList<>();

        if (productId != null) {
            sql.append("AND m.product_id = ?\n");
            params.add(productId);
        }

        if (movementType != null && !movementType.trim().isEmpty()) {
            sql.append("AND m.movement_type = ?\n");
            params.add(movementType.trim());
        }

        String normalizedDirection = direction == null ? "ALL" : direction.trim().toUpperCase();
        if (!"ALL".equals(normalizedDirection)) {
            switch (normalizedDirection) {
                case "ENTRADA" -> sql.append("AND COALESCE(m.quantity_in, 0) > 0\n");
                case "SALIDA" -> sql.append("AND COALESCE(m.quantity_out, 0) > 0\n");
                case "NEUTRO" -> sql.append("AND COALESCE(m.quantity_in, 0) <= 0 AND COALESCE(m.quantity_out, 0) <= 0\n");
                default -> sql.append("AND 1=0\n");
            }
        }

        String normalizedSource = source == null ? "ALL" : source.trim().toUpperCase();
        if (!"ALL".equals(normalizedSource)) {
            switch (normalizedSource) {
                case "PURCHASE" -> sql.append("AND m.source_table = 'purchase_item'\n");
                case "SALE" -> sql.append("AND m.source_table IN ('sale_item', 'credit_note_item')\n");
                case "COUNTER_SALE" -> sql.append("AND m.source_table = 'counter_sale_item'\n");
                case "ADJUSTMENT" -> sql.append("AND m.source_table = 'product_stock_adjustment'\n");
                case "OTHER" -> sql.append("AND m.source_table NOT IN ('purchase_item', 'sale_item', 'counter_sale_item', 'credit_note_item', 'product_stock_adjustment')\n");
                default -> sql.append("AND 1=0\n");
            }
        }

        if (dateFrom != null) {
            sql.append("AND m.created_at >= ?\n");
            params.add(dateFrom.atStartOfDay());
        }

        if (dateTo != null) {
            sql.append("AND m.created_at < ?\n");
            params.add(dateTo.plusDays(1).atStartOfDay());
        }

        return new SqlWhere(sql.toString(), params);
    }

    private SqlWhere buildWhere(
            String query,
            Long productId,
            String category,
            String brand,
            String model,
            String movementType,
            String direction,
            String source,
            String docType,
            String series,
            String number,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        StringBuilder sql = new StringBuilder("WHERE 1=1\n");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            String like = QueryMapper.formatAsLikeParam(query);
            sql.append("""
                    AND (
                         sku ILIKE ?
                      OR product_name ILIKE ?
                      OR COALESCE(brand, '') ILIKE ?
                      OR COALESCE(model, '') ILIKE ?
                      OR COALESCE(category, '') ILIKE ?
                      OR COALESCE(source_series, '') ILIKE ?
                      OR COALESCE(source_number, '') ILIKE ?
                      OR COALESCE(counterpart_document_number, '') ILIKE ?
                      OR COALESCE(counterpart_name, '') ILIKE ?
                    )
                    """);
            for (int i = 0; i < 9; i++) {
                params.add(like);
            }
        }

        if (productId != null) {
            sql.append("AND product_id = ?\n");
            params.add(productId);
        }

        if (category != null && !category.trim().isEmpty()) {
            sql.append("AND category = ?\n");
            params.add(category.trim());
        }

        if (brand != null && !brand.trim().isEmpty()) {
            sql.append("AND COALESCE(brand, '') ILIKE ?\n");
            params.add("%" + brand.trim() + "%");
        }

        if (model != null && !model.trim().isEmpty()) {
            sql.append("AND COALESCE(model, '') ILIKE ?\n");
            params.add("%" + model.trim() + "%");
        }

        if (movementType != null && !movementType.trim().isEmpty()) {
            sql.append("AND movement_type = ?\n");
            params.add(movementType.trim());
        }

        if (direction != null && !"ALL".equals(direction)) {
            sql.append("AND direction = ?\n");
            params.add(direction);
        }

        if (source != null && !"ALL".equals(source)) {
            sql.append("AND source_filter = ?\n");
            params.add(source);
        }

        if (docType != null && !docType.trim().isEmpty()) {
            sql.append("AND source_document_type = ?\n");
            params.add(docType.trim());
        }

        if (series != null && !series.trim().isEmpty()) {
            sql.append("AND COALESCE(source_series, '') ILIKE ?\n");
            params.add("%" + series.trim() + "%");
        }

        if (number != null && !number.trim().isEmpty()) {
            sql.append("AND COALESCE(source_number, '') ILIKE ?\n");
            params.add("%" + number.trim() + "%");
        }

        if (dateFrom != null) {
            LocalDateTime from = dateFrom.atStartOfDay();
            sql.append("AND movement_date >= ?\n");
            params.add(from);
        }

        if (dateTo != null) {
            LocalDateTime toExclusive = dateTo.plusDays(1).atStartOfDay();
            sql.append("AND movement_date < ?\n");
            params.add(toExclusive);
        }

        return new SqlWhere(sql.toString(), params);
    }

    private String baseSql() {
        return baseSql("");
    }

    private String baseSql(String movementWhereSql) {
        String safeMovementWhereSql = movementWhereSql == null ? "" : movementWhereSql;

        return """
                WITH enriched AS (
                  SELECT
                    m.id,
                    m.created_at AS movement_date,

                    p.id AS product_id,
                    p.sku,
                    p.name AS product_name,
                    p.category,
                    p.brand,
                    p.model,
                    p.presentation,
                    p.manage_by_serial,
                    p.existence_type_code,

                    m.movement_type,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN 'COMPRA'
                      WHEN m.source_table = 'sale_item' THEN 'VENTA'
                      WHEN m.source_table = 'counter_sale_item' THEN 'VENTANILLA'
                      WHEN m.source_table = 'credit_note_item' THEN 'NOTA DE CREDITO'
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'AJUSTE'
                      WHEN m.movement_type ILIKE '%RETURN%' THEN 'DEVOLUCION'
                      ELSE 'OTRO'
                    END AS movement_label,
                    CASE
                      WHEN COALESCE(m.quantity_in, 0) > 0 THEN 'ENTRADA'
                      WHEN COALESCE(m.quantity_out, 0) > 0 THEN 'SALIDA'
                      ELSE 'NEUTRO'
                    END AS direction,

                    CASE
                      WHEN m.source_table = 'purchase_item' THEN 'PURCHASE'
                      WHEN m.source_table = 'sale_item' THEN 'SALE'
                      WHEN m.source_table = 'counter_sale_item' THEN 'COUNTER_SALE'
                      WHEN m.source_table = 'credit_note_item' THEN 'SALE'
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'ADJUSTMENT'
                      ELSE 'OTHER'
                    END AS source_filter,

                    m.source_table,
                    m.source_id,

                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.document_type
                      WHEN m.source_table = 'sale_item' THEN s.doc_type
                      WHEN m.source_table = 'counter_sale_item' THEN COALESCE(NULLIF(cs.associated_doc_type, ''), cs_link_emit.emitted_doc_type, cs_combo_emit.emitted_doc_type, cs_sunat.doc_type, 'VENTANILLA')
                      WHEN m.source_table = 'credit_note_item' THEN 'NOTA_CREDITO'
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'AJUSTE'
                      ELSE m.source_table
                    END AS source_document_type,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.document_series
                      WHEN m.source_table = 'sale_item' THEN s.series
                      WHEN m.source_table = 'counter_sale_item' THEN COALESCE(NULLIF(cs.associated_series, ''), cs_link_emit.emitted_series, cs_combo_emit.emitted_series, cs_sunat.series, cs.series)
                      WHEN m.source_table = 'credit_note_item' THEN cn.series
                      WHEN m.source_table = 'product_stock_adjustment' THEN NULL
                      ELSE NULL
                    END AS source_series,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.document_number
                      WHEN m.source_table = 'sale_item' THEN s.number::text
                      WHEN m.source_table = 'counter_sale_item' THEN COALESCE(cs.associated_number::text, cs_link_emit.emitted_number::text, cs_combo_emit.emitted_number::text, cs_sunat.number::text, cs.number::text)
                      WHEN m.source_table = 'credit_note_item' THEN cn.number::text
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.id::text
                      ELSE m.source_id::text
                    END AS source_number,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN COALESCE(pu.entry_date, pu.issue_date)
                      WHEN m.source_table = 'sale_item' THEN s.issue_date
                      WHEN m.source_table = 'counter_sale_item' THEN COALESCE(cs_sunat.issue_date, cs_link_emit.issue_date, cs_combo_emit.issue_date, cs.associated_at::date, cs.issue_date)
                      WHEN m.source_table = 'credit_note_item' THEN cn.issue_date
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.created_at::date
                      ELSE m.created_at::date
                    END AS source_issue_date,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.status
                      WHEN m.source_table = 'sale_item' THEN s.status
                      WHEN m.source_table = 'counter_sale_item' THEN cs.status
                      WHEN m.source_table = 'credit_note_item' THEN cn.sunat_status
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.movement_type
                      ELSE NULL
                    END AS source_status,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pi.line_number
                      WHEN m.source_table = 'sale_item' THEN si.line_number
                      WHEN m.source_table = 'counter_sale_item' THEN csi.line_number
                      WHEN m.source_table = 'credit_note_item' THEN cni.line_number
                      ELSE NULL
                    END AS source_line_number,

                    CASE
                      WHEN m.source_table = 'purchase_item' THEN 'PROVEEDOR'
                      WHEN m.source_table = 'sale_item' THEN 'CLIENTE'
                      WHEN m.source_table = 'counter_sale_item' THEN 'CLIENTE'
                      WHEN m.source_table = 'credit_note_item' THEN 'CLIENTE'
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'INTERNO'
                      ELSE 'OTRO'
                    END AS counterpart_type,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.supplier_ruc
                      WHEN m.source_table = 'sale_item' THEN s.customer_doc_number
                      WHEN m.source_table = 'counter_sale_item' THEN cs.customer_doc_number
                      WHEN m.source_table = 'credit_note_item' THEN cn.customer_doc_number
                      ELSE NULL
                    END AS counterpart_document_number,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.supplier_business_name
                      WHEN m.source_table = 'sale_item' THEN s.customer_name
                      WHEN m.source_table = 'counter_sale_item' THEN cs.customer_name
                      WHEN m.source_table = 'credit_note_item' THEN cn.customer_name
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'AJUSTE INTERNO'
                      ELSE NULL
                    END AS counterpart_name,

                    COALESCE(m.quantity_in, 0) AS quantity_in,
                    COALESCE(m.quantity_out, 0) AS quantity_out,
                    CASE
                      WHEN COALESCE(m.quantity_in, 0) > 0 THEN COALESCE(m.quantity_in, 0)
                      WHEN COALESCE(m.quantity_out, 0) > 0 THEN COALESCE(m.quantity_out, 0)
                      ELSE 0
                    END AS movement_quantity,

                    CASE
                      WHEN m.balance_qty IS NULL THEN NULL
                      ELSE m.balance_qty - COALESCE(m.quantity_in, 0) + COALESCE(m.quantity_out, 0)
                    END AS stock_before,
                    m.balance_qty AS stock_after,

                    m.unit_cost,
                    m.total_cost,
                    m.balance_cost AS average_cost_after,

                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pi.unit_cost
                      WHEN m.source_table = 'sale_item' THEN si.unit_price
                      WHEN m.source_table = 'counter_sale_item' THEN COALESCE(cs_link_emit.emitted_unit_price, cs_combo_emit.emitted_unit_price, csi.unit_price)
                      WHEN m.source_table = 'credit_note_item' THEN cni.unit_price
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.unit_cost
                      ELSE NULL
                    END AS source_unit_price,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pi.total_cost
                      WHEN m.source_table = 'sale_item' THEN si.revenue_total
                      WHEN m.source_table = 'counter_sale_item' THEN COALESCE(cs_link_emit.emitted_revenue_total, cs_combo_emit.emitted_revenue_total, csi.revenue_total)
                      WHEN m.source_table = 'credit_note_item' THEN cni.revenue_total
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.total_cost
                      ELSE NULL
                    END AS source_line_total,

                    psa.reason AS adjustment_reason,
                    COALESCE(psa.note, pu.notes, s.notes, cs.notes, cn.reason) AS note,

                    CASE
                      WHEN m.source_table = 'purchase_item'
                           AND UPPER(COALESCE(pu.document_type, '')) IN ('FACTURA', 'BOLETA') THEN TRUE
                      WHEN m.source_table = 'sale_item'
                           AND COALESCE(m.quantity_out, 0) > 0
                           AND UPPER(COALESCE(s.doc_type, '')) IN ('FACTURA', 'BOLETA')
                           AND UPPER(COALESCE(s.status, '')) = 'EMITIDA' THEN TRUE
                      WHEN m.source_table = 'counter_sale_item'
                           AND m.movement_type = 'OUT_COUNTER_SALE'
                           AND UPPER(COALESCE(cs.associated_doc_type, cs_link_emit.emitted_doc_type, cs_combo_emit.emitted_doc_type, cs_sunat.doc_type, '')) IN ('FACTURA', 'BOLETA')
                           AND (
                                COALESCE(cs.associated_to_sunat, FALSE) = TRUE
                             OR cs_link_emit.counter_sale_item_id IS NOT NULL
                             OR cs_combo_emit.counter_sale_item_id IS NOT NULL
                             OR UPPER(COALESCE(cs_sunat.status, '')) = 'EMITIDA'
                           )
                           THEN TRUE
                      WHEN m.source_table = 'credit_note_item'
                           AND m.movement_type = 'IN_RETURN'
                           AND UPPER(COALESCE(cn.status, '')) = 'EMITIDA'
                           THEN TRUE
                      ELSE FALSE
                    END AS tax_export_eligible

                  FROM product_stock_movement m
                  INNER JOIN product p
                          ON p.id = m.product_id

                  LEFT JOIN purchase_item pi
                         ON m.source_table = 'purchase_item'
                        AND m.source_id = pi.id
                  LEFT JOIN purchase pu
                         ON pu.id = pi.purchase_id

                  LEFT JOIN sale_item si
                         ON m.source_table = 'sale_item'
                        AND m.source_id = si.id
                  LEFT JOIN sale s
                         ON s.id = si.sale_id

                  LEFT JOIN counter_sale_item csi
                         ON m.source_table = 'counter_sale_item'
                        AND m.source_id = csi.id
                  LEFT JOIN counter_sale cs
                         ON cs.id = csi.counter_sale_id
                  LEFT JOIN sale cs_sunat
                         ON m.source_table = 'counter_sale_item'
                        AND cs_sunat.id = cs.associated_sale_id
                  LEFT JOIN LATERAL (
                    SELECT li.counter_sale_item_id,
                           li.emitted_unit_price,
                           li.emitted_revenue_total,
                           l.emitted_doc_type,
                           l.emitted_series,
                           l.emitted_number,
                           COALESCE(sl.issue_date, l.associated_at::date) AS issue_date
                      FROM sale_counter_sale_sunat_link_item li
                      JOIN sale_counter_sale_sunat_link l
                        ON l.sale_id = li.sale_id
                       AND l.counter_sale_id = li.counter_sale_id
                      LEFT JOIN sale sl
                        ON sl.id = l.sale_id
                     WHERE m.source_table = 'counter_sale_item'
                       AND li.counter_sale_item_id = csi.id
                       AND l.reservation_status = 'ACEPTADO'
                     ORDER BY COALESCE(l.associated_at, l.updated_at, l.reserved_at) DESC NULLS LAST,
                              li.id DESC
                     LIMIT 1
                  ) cs_link_emit ON TRUE
                  LEFT JOIN LATERAL (
                    SELECT cl.counter_sale_item_id,
                           cl.emitted_unit_price,
                           cl.emitted_revenue_total,
                           c.emitted_doc_type,
                           c.emitted_series,
                           c.emitted_number,
                           COALESCE(sg.issue_date, c.issue_date, c.associated_at::date) AS issue_date
                      FROM counter_sale_sunat_combo_line cl
                      JOIN counter_sale_sunat_combo c
                        ON c.id = cl.combo_id
                      LEFT JOIN sale sg
                        ON sg.id = c.generated_sale_id
                     WHERE m.source_table = 'counter_sale_item'
                       AND cl.counter_sale_item_id = csi.id
                       AND c.combo_status = 'ACEPTADO'
                     ORDER BY COALESCE(c.associated_at, c.updated_at, c.created_at) DESC NULLS LAST,
                              cl.id DESC
                     LIMIT 1
                  ) cs_combo_emit ON TRUE

                  LEFT JOIN credit_note_item cni
                         ON m.source_table = 'credit_note_item'
                        AND m.source_id = cni.id
                  LEFT JOIN credit_note cn
                         ON cn.id = cni.credit_note_id

                  LEFT JOIN product_stock_adjustment psa
                         ON m.source_table = 'product_stock_adjustment'
                        AND m.source_id = psa.id
                  """
                + safeMovementWhereSql
                + """
                )
                """;
    }

    private record SqlWhere(String sql, List<Object> params) {
    }
}
