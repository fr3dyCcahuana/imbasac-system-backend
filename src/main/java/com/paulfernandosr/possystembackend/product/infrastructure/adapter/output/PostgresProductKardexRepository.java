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
        String baseSql = baseSql();
        SqlWhere where = buildWhere(
                query,
                productId,
                category,
                brand,
                model,
                movementType,
                direction,
                source,
                docType,
                series,
                number,
                dateFrom,
                dateTo
        );

        String countSql = baseSql + "\nSELECT COUNT(1) FROM enriched\n" + where.sql();
        long totalElements = jdbcClient.sql(countSql)
                .params(where.params().toArray())
                .query(Long.class)
                .single();

        int size = pageable.getSize();
        int page = pageable.getNumber();

        List<Object> selectParams = new ArrayList<>(where.params());
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

                    m.movement_type,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN 'COMPRA'
                      WHEN m.source_table = 'sale_item' THEN 'VENTA'
                      WHEN m.source_table = 'counter_sale_item' THEN 'VENTANILLA'
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
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'ADJUSTMENT'
                      ELSE 'OTHER'
                    END AS source_filter,

                    m.source_table,
                    m.source_id,

                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.document_type
                      WHEN m.source_table = 'sale_item' THEN s.doc_type
                      WHEN m.source_table = 'counter_sale_item' THEN 'VENTANILLA'
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'AJUSTE'
                      ELSE m.source_table
                    END AS source_document_type,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.document_series
                      WHEN m.source_table = 'sale_item' THEN s.series
                      WHEN m.source_table = 'counter_sale_item' THEN cs.series
                      WHEN m.source_table = 'product_stock_adjustment' THEN NULL
                      ELSE NULL
                    END AS source_series,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.document_number
                      WHEN m.source_table = 'sale_item' THEN s.number::text
                      WHEN m.source_table = 'counter_sale_item' THEN cs.number::text
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.id::text
                      ELSE m.source_id::text
                    END AS source_number,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN COALESCE(pu.entry_date, pu.issue_date)
                      WHEN m.source_table = 'sale_item' THEN s.issue_date
                      WHEN m.source_table = 'counter_sale_item' THEN cs.issue_date
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.created_at::date
                      ELSE m.created_at::date
                    END AS source_issue_date,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.status
                      WHEN m.source_table = 'sale_item' THEN s.status
                      WHEN m.source_table = 'counter_sale_item' THEN cs.status
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.movement_type
                      ELSE NULL
                    END AS source_status,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pi.line_number
                      WHEN m.source_table = 'sale_item' THEN si.line_number
                      WHEN m.source_table = 'counter_sale_item' THEN csi.line_number
                      ELSE NULL
                    END AS source_line_number,

                    CASE
                      WHEN m.source_table = 'purchase_item' THEN 'PROVEEDOR'
                      WHEN m.source_table = 'sale_item' THEN 'CLIENTE'
                      WHEN m.source_table = 'counter_sale_item' THEN 'CLIENTE'
                      WHEN m.source_table = 'product_stock_adjustment' THEN 'INTERNO'
                      ELSE 'OTRO'
                    END AS counterpart_type,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.supplier_ruc
                      WHEN m.source_table = 'sale_item' THEN s.customer_doc_number
                      WHEN m.source_table = 'counter_sale_item' THEN cs.customer_doc_number
                      ELSE NULL
                    END AS counterpart_document_number,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pu.supplier_business_name
                      WHEN m.source_table = 'sale_item' THEN s.customer_name
                      WHEN m.source_table = 'counter_sale_item' THEN cs.customer_name
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
                      WHEN m.source_table = 'counter_sale_item' THEN csi.unit_price
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.unit_cost
                      ELSE NULL
                    END AS source_unit_price,
                    CASE
                      WHEN m.source_table = 'purchase_item' THEN pi.total_cost
                      WHEN m.source_table = 'sale_item' THEN si.revenue_total
                      WHEN m.source_table = 'counter_sale_item' THEN csi.revenue_total
                      WHEN m.source_table = 'product_stock_adjustment' THEN psa.total_cost
                      ELSE NULL
                    END AS source_line_total,

                    psa.reason AS adjustment_reason,
                    COALESCE(psa.note, pu.notes, s.notes, cs.notes) AS note

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

                  LEFT JOIN product_stock_adjustment psa
                         ON m.source_table = 'product_stock_adjustment'
                        AND m.source_id = psa.id
                )
                """;
    }

    private record SqlWhere(String sql, List<Object> params) {
    }
}
