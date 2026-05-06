package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.port.output.CounterSaleQueryRepository;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresQueryRepository implements CounterSaleQueryRepository {

    private final JdbcClient jdbcClient;

    private void appendCommonFilters(StringBuilder sql,
                                     List<Object> params,
                                     String likeParam,
                                     String series,
                                     Long number,
                                     String status) {
        if (series != null && !series.isBlank()) {
            sql.append(" AND UPPER(TRIM(cs.series)) = ? ");
            params.add(series);
        }
        if (number != null) {
            sql.append(" AND cs.number = ? ");
            params.add(number);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND cs.status = ? ");
            params.add(status);
        }
        sql.append("""
            AND (
                ? = '%'
                OR COALESCE(cs.customer_name, '') ILIKE ?
                OR COALESCE(cs.customer_doc_number, '') ILIKE ?
                OR (cs.series || '-' || CAST(cs.number AS TEXT)) ILIKE ?
                OR (COALESCE(cs.associated_series, '') || '-' || COALESCE(CAST(cs.associated_number AS TEXT), '')) ILIKE ?
            )
        """);
        params.add(likeParam);
        params.add(likeParam);
        params.add(likeParam);
        params.add(likeParam);
        params.add(likeParam);
    }

    @Override
    public long countCounterSales(String likeParam, String series, Long number, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(1)
              FROM counter_sale cs
             WHERE 1 = 1
        """);
        List<Object> params = new ArrayList<>();
        appendCommonFilters(sql, params, likeParam, series, number, status);
        return jdbcClient.sql(sql.toString()).params(params).query(Long.class).single();
    }

    @Override
    public List<CounterSaleSummaryResponse> findCounterSalesPage(String likeParam, String series, Long number,
                                                                 String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT cs.id AS counter_sale_id,
                   cs.series AS series,
                   cs.number AS number,
                   cs.issue_date AS issue_date,
                   cs.customer_doc_number AS customer_doc_number,
                   cs.customer_name AS customer_name,
                   cs.total AS total,
                   cs.status AS status,
                   cs.associated_to_sunat AS associated_to_sunat,
                   (
                       SELECT csm.combo_id
                         FROM counter_sale_sunat_combo_member csm
                         JOIN counter_sale_sunat_combo csc
                           ON csc.id = csm.combo_id
                        WHERE csm.counter_sale_id = cs.id
                          AND csc.combo_status = 'ACEPTADO'
                        ORDER BY csc.associated_at DESC NULLS LAST, csc.id DESC
                        LIMIT 1
                   ) AS associated_combo_id,
                   cs.associated_sale_id AS associated_sale_id,
                   cs.associated_doc_type AS associated_doc_type,
                   cs.associated_series AS associated_series,
                   cs.associated_number AS associated_number,
                   cs.associated_at AS associated_at,
                   cs.created_at AS created_at,
                   cs.updated_at AS updated_at
              FROM counter_sale cs
             WHERE 1 = 1
        """);
        List<Object> params = new ArrayList<>();
        appendCommonFilters(sql, params, likeParam, series, number, status);
        sql.append("""
            ORDER BY cs.issue_date DESC, cs.id DESC
            LIMIT ? OFFSET ?
        """);
        params.add(limit);
        params.add(offset);

        RowMapper<CounterSaleSummaryResponse> mapper = (rs, rowNum) -> {
            boolean associatedToSunat = Boolean.TRUE.equals(rs.getObject("associated_to_sunat", Boolean.class));
            String statusValue = rs.getString("status");
            return CounterSaleSummaryResponse.builder()
                    .counterSaleId(rs.getLong("counter_sale_id"))
                    .series(rs.getString("series"))
                    .number(rs.getLong("number"))
                    .issueDate(rs.getDate("issue_date").toLocalDate())
                    .customerDocNumber(rs.getString("customer_doc_number"))
                    .customerName(rs.getString("customer_name"))
                    .total(rs.getBigDecimal("total"))
                    .status(statusValue)
                    .associatedToSunat(associatedToSunat)
                    .associatedComboId((Long) rs.getObject("associated_combo_id"))
                    .associatedSaleId((Long) rs.getObject("associated_sale_id"))
                    .associatedDocType(rs.getString("associated_doc_type"))
                    .associatedSeries(rs.getString("associated_series"))
                    .associatedNumber((Long) rs.getObject("associated_number"))
                    .associatedAt(rs.getTimestamp("associated_at") != null ? rs.getTimestamp("associated_at").toLocalDateTime() : null)
                    .canVoid("EMITIDA".equalsIgnoreCase(statusValue) && !associatedToSunat)
                    .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                    .build();
        };

        return jdbcClient.sql(sql.toString()).params(params).query(mapper).list();
    }

    @Override
    public List<CounterSaleSummaryItemResponse> findSummaryItemsByCounterSaleIds(List<Long> counterSaleIds) {
        if (counterSaleIds == null || counterSaleIds.isEmpty()) return List.of();
        String placeholders = counterSaleIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("""
            SELECT csi.counter_sale_id AS counter_sale_id,
                   csi.id AS counter_sale_item_id,
                   csi.line_number AS line_number,
                   csi.product_id AS product_id,
                   csi.quantity AS quantity,
                   csi.sku AS sku,
                   csi.description AS description,
                   p.warehouse_location AS product_location,
                   csi.presentation AS presentation,
                   csi.unit_price AS unit_price,
                   csi.revenue_total AS revenue_total
              FROM counter_sale_item csi
              JOIN product p ON p.id = csi.product_id
             WHERE csi.counter_sale_id IN (%s)
             ORDER BY csi.counter_sale_id ASC, csi.line_number ASC
        """, placeholders);
        RowMapper<CounterSaleSummaryItemResponse> mapper = (rs, rowNum) -> CounterSaleSummaryItemResponse.builder()
                .counterSaleId(rs.getLong("counter_sale_id"))
                .counterSaleItemId(rs.getLong("counter_sale_item_id"))
                .lineNumber(rs.getInt("line_number"))
                .productId(rs.getLong("product_id"))
                .quantity(rs.getBigDecimal("quantity"))
                .sku(rs.getString("sku"))
                .description(rs.getString("description"))
                .productLocation(rs.getString("product_location"))
                .presentation(rs.getString("presentation"))
                .unitPrice(rs.getBigDecimal("unit_price"))
                .revenueTotal(rs.getBigDecimal("revenue_total"))
                .build();
        return jdbcClient.sql(sql).params(counterSaleIds.toArray()).query(mapper).list();
    }

    @Override
    public CounterSaleDetailResponse findCounterSaleDetail(Long counterSaleId) {
        String sql = """
            SELECT cs.id AS counter_sale_id,
                   cs.station_id AS station_id,
                   cs.sale_session_id AS sale_session_id,
                   cs.created_by AS created_by,
                   u_create.username AS created_by_username,
                   cs.series AS series,
                   cs.number AS number,
                   cs.issue_date AS issue_date,
                   cs.currency AS currency,
                   cs.exchange_rate AS exchange_rate,
                   cs.price_list AS price_list,
                   cs.customer_id AS customer_id,
                   cs.customer_doc_type AS customer_doc_type,
                   cs.customer_doc_number AS customer_doc_number,
                   cs.customer_name AS customer_name,
                   cs.customer_address AS customer_address,
                   cs.tax_status AS tax_status,
                   cs.igv_rate AS igv_rate,
                   cs.igv_included AS igv_included,
                   cs.subtotal AS subtotal,
                   cs.discount_total AS discount_total,
                   cs.igv_amount AS igv_amount,
                   cs.total AS total,
                   cs.gift_cost_total AS gift_cost_total,
                   cs.notes AS notes,
                   cs.status AS status,
                   cs.associated_to_sunat AS associated_to_sunat,
                   (
                       SELECT csm.combo_id
                         FROM counter_sale_sunat_combo_member csm
                         JOIN counter_sale_sunat_combo csc
                           ON csc.id = csm.combo_id
                        WHERE csm.counter_sale_id = cs.id
                          AND csc.combo_status = 'ACEPTADO'
                        ORDER BY csc.associated_at DESC NULLS LAST, csc.id DESC
                        LIMIT 1
                   ) AS associated_combo_id,
                   cs.associated_sale_id AS associated_sale_id,
                   cs.associated_doc_type AS associated_doc_type,
                   cs.associated_series AS associated_series,
                   cs.associated_number AS associated_number,
                   cs.associated_at AS associated_at,
                   cs.voided_at AS voided_at,
                   cs.voided_by AS voided_by,
                   u_void.username AS voided_by_username,
                   cs.void_reason AS void_reason,
                   cs.created_at AS created_at,
                   cs.updated_at AS updated_at
              FROM counter_sale cs
              JOIN users u_create ON u_create.id = cs.created_by
              LEFT JOIN users u_void ON u_void.id = cs.voided_by
             WHERE cs.id = ?
        """;
        RowMapper<CounterSaleDetailResponse> mapper = (rs, rowNum) -> {
            boolean associatedToSunat = Boolean.TRUE.equals(rs.getObject("associated_to_sunat", Boolean.class));
            String statusValue = rs.getString("status");
            return CounterSaleDetailResponse.builder()
                    .counterSaleId(rs.getLong("counter_sale_id"))
                    .stationId(rs.getLong("station_id"))
                    .saleSessionId((Long) rs.getObject("sale_session_id"))
                    .createdBy(rs.getLong("created_by"))
                    .createdByUsername(rs.getString("created_by_username"))
                    .series(rs.getString("series"))
                    .number(rs.getLong("number"))
                    .issueDate(rs.getDate("issue_date").toLocalDate())
                    .currency(rs.getString("currency"))
                    .exchangeRate(rs.getBigDecimal("exchange_rate"))
                    .priceList(rs.getString("price_list"))
                    .customerId((Long) rs.getObject("customer_id"))
                    .customerDocType(rs.getString("customer_doc_type"))
                    .customerDocNumber(rs.getString("customer_doc_number"))
                    .customerName(rs.getString("customer_name"))
                    .customerAddress(rs.getString("customer_address"))
                    .taxStatus(rs.getString("tax_status"))
                    .igvRate(rs.getBigDecimal("igv_rate"))
                    .igvIncluded(rs.getObject("igv_included", Boolean.class))
                    .subtotal(rs.getBigDecimal("subtotal"))
                    .discountTotal(rs.getBigDecimal("discount_total"))
                    .igvAmount(rs.getBigDecimal("igv_amount"))
                    .total(rs.getBigDecimal("total"))
                    .giftCostTotal(rs.getBigDecimal("gift_cost_total"))
                    .notes(rs.getString("notes"))
                    .status(statusValue)
                    .associatedToSunat(associatedToSunat)
                    .associatedComboId((Long) rs.getObject("associated_combo_id"))
                    .associatedSaleId((Long) rs.getObject("associated_sale_id"))
                    .associatedDocType(rs.getString("associated_doc_type"))
                    .associatedSeries(rs.getString("associated_series"))
                    .associatedNumber((Long) rs.getObject("associated_number"))
                    .associatedAt(rs.getTimestamp("associated_at") != null ? rs.getTimestamp("associated_at").toLocalDateTime() : null)
                    .canVoid("EMITIDA".equalsIgnoreCase(statusValue) && !associatedToSunat)
                    .voidInfo(CounterSaleVoidInfoResponse.builder()
                            .voidedAt(rs.getTimestamp("voided_at") != null ? rs.getTimestamp("voided_at").toLocalDateTime() : null)
                            .voidedBy((Long) rs.getObject("voided_by"))
                            .voidedByUsername(rs.getString("voided_by_username"))
                            .voidReason(rs.getString("void_reason"))
                            .build())
                    .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                    .build();
        };
        return jdbcClient.sql(sql).param(counterSaleId).query(mapper).optional().orElse(null);
    }

    @Override
    public List<CounterSaleItemResponse> findCounterSaleItems(Long counterSaleId) {
        String sql = """
            SELECT csi.id AS counter_sale_item_id,
                   csi.line_number AS line_number,
                   csi.product_id AS product_id,
                   csi.sku AS sku,
                   csi.description AS description,
                   p.warehouse_location AS product_location,
                   csi.presentation AS presentation,
                   csi.factor AS factor,
                   csi.quantity AS quantity,
                   csi.unit_price AS unit_price,
                   csi.discount_percent AS discount_percent,
                   csi.discount_amount AS discount_amount,
                   csi.line_kind AS line_kind,
                   csi.gift_reason AS gift_reason,
                   csi.affects_stock AS affects_stock,
                   csi.unit_cost_snapshot AS unit_cost_snapshot,
                   csi.total_cost_snapshot AS total_cost_snapshot,
                   csi.revenue_total AS revenue_total,
                   csi.created_at AS created_at,
                   p.category AS product_category,
                   p.brand AS v_marca,
                   p.model AS v_modelo,
                   vs.engine_capacity AS v_engine_capacity,
                   vs.fuel AS v_combustible,
                   vs.cylinders AS v_num_cilindros,
                   vs.net_weight AS v_peso_neto,
                   vs.gross_weight AS v_peso_bruto
              FROM counter_sale_item csi
              JOIN product p ON p.id = csi.product_id
              LEFT JOIN product_vehicle_specs vs ON vs.product_id = p.id
             WHERE csi.counter_sale_id = ?
             ORDER BY csi.line_number ASC
        """;
        RowMapper<CounterSaleItemResponse> mapper = (rs, rowNum) -> {
            String category = rs.getString("product_category");
            String cat = category != null ? category.trim().toUpperCase() : null;
            boolean isVehicle = "MOTOR".equals(cat) || "MOTOCICLETAS".equals(cat) || "MOTOCICLETA".equals(cat);
            VehicleDetailsResponse vehicleDetails = null;
            if (isVehicle) {
                String cap = null;
                var capVal = rs.getBigDecimal("v_engine_capacity");
                if (capVal != null) {
                    cap = capVal.stripTrailingZeros().toPlainString() + "CC";
                }
                vehicleDetails = VehicleDetailsResponse.builder()
                        .marca(rs.getString("v_marca"))
                        .modelo(rs.getString("v_modelo"))
                        .capacidadMotor(cap)
                        .combustible(rs.getString("v_combustible"))
                        .numCilindros((Integer) rs.getObject("v_num_cilindros"))
                        .pesoNeto(rs.getBigDecimal("v_peso_neto"))
                        .pesoBruto(rs.getBigDecimal("v_peso_bruto"))
                        .build();
            }
            return CounterSaleItemResponse.builder()
                    .counterSaleItemId(rs.getLong("counter_sale_item_id"))
                    .lineNumber(rs.getInt("line_number"))
                    .productId(rs.getLong("product_id"))
                    .sku(rs.getString("sku"))
                    .description(rs.getString("description"))
                    .productLocation(rs.getString("product_location"))
                    .presentation(rs.getString("presentation"))
                    .factor(rs.getBigDecimal("factor"))
                    .quantity(rs.getBigDecimal("quantity"))
                    .unitPrice(rs.getBigDecimal("unit_price"))
                    .discountPercent(rs.getBigDecimal("discount_percent"))
                    .discountAmount(rs.getBigDecimal("discount_amount"))
                    .lineKind(rs.getString("line_kind"))
                    .giftReason(rs.getString("gift_reason"))
                    .affectsStock(rs.getObject("affects_stock", Boolean.class))
                    .unitCostSnapshot(rs.getBigDecimal("unit_cost_snapshot"))
                    .totalCostSnapshot(rs.getBigDecimal("total_cost_snapshot"))
                    .revenueTotal(rs.getBigDecimal("revenue_total"))
                    .productCategory(category)
                    .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .vehicleDetails(vehicleDetails)
                    .build();
        };
        return jdbcClient.sql(sql).param(counterSaleId).query(mapper).list();
    }

    @Override
    public List<CounterSaleSerialUnitResponse> findCounterSaleItemSerialUnits(Long counterSaleId) {
        String sql = """
            SELECT cssu.counter_sale_item_id AS counter_sale_item_id,
                   psu.id AS serial_unit_id,
                   psu.status AS status,
                   psu.vin AS vin,
                   psu.chassis_number AS chassis_number,
                   psu.engine_number AS engine_number,
                   psu.color AS color,
                   psu.year_make AS year_make
              FROM counter_sale_serial_unit cssu
              JOIN counter_sale_item csi ON csi.id = cssu.counter_sale_item_id
              JOIN product_serial_unit psu ON psu.id = cssu.serial_unit_id
             WHERE csi.counter_sale_id = ?
             ORDER BY cssu.counter_sale_item_id ASC, psu.id ASC
        """;
        RowMapper<CounterSaleSerialUnitResponse> mapper = (rs, rowNum) -> CounterSaleSerialUnitResponse.builder()
                .counterSaleItemId(rs.getLong("counter_sale_item_id"))
                .serialUnitId(rs.getLong("serial_unit_id"))
                .status(rs.getString("status"))
                .vin(rs.getString("vin"))
                .chassisNumber(rs.getString("chassis_number"))
                .engineNumber(rs.getString("engine_number"))
                .color(rs.getString("color"))
                .yearMake((Integer) rs.getObject("year_make"))
                .build();
        return jdbcClient.sql(sql).param(counterSaleId).query(mapper).list();
    }

    @Override
    public CounterSalePaymentResponse findCounterSalePayment(Long counterSaleId) {
        String sql = """
            SELECT method, amount, paid_at
              FROM counter_sale_payment
             WHERE counter_sale_id = ?
        """;
        RowMapper<CounterSalePaymentResponse> mapper = (rs, rowNum) -> CounterSalePaymentResponse.builder()
                .method(rs.getString("method"))
                .amount(rs.getBigDecimal("amount"))
                .paidAt(rs.getTimestamp("paid_at").toLocalDateTime())
                .build();
        return jdbcClient.sql(sql).param(counterSaleId).query(mapper).optional().orElse(null);
    }

    @Override
    public ElectronicReceiptPrintableResponse findElectronicReceiptPrintableHeaderByComboId(Long comboId) {
        String sql = """
            SELECT s.id AS sale_id,
                   s.created_by AS created_by,
                   s.doc_type AS doc_type,
                   s.series AS series,
                   s.number AS number,
                   s.issue_date AS issue_date,
                   s.currency AS currency,
                   s.customer_doc_type AS customer_doc_type,
                   s.customer_doc_number AS customer_doc_number,
                   s.customer_name AS customer_name,
                   s.customer_address AS customer_address,
                   s.tax_status AS tax_status,
                   s.payment_type AS payment_type,
                   s.due_date AS due_date,
                   s.subtotal AS subtotal,
                   s.discount_total AS discount_total,
                   s.igv_amount AS igv_amount,
                   s.total AS total,
                   sp.method AS payment_method,
                   sp.amount AS payment_amount,
                   sp.paid_at AS payment_paid_at
              FROM counter_sale_sunat_combo cssc
              JOIN sale s ON s.id = cssc.generated_sale_id
              LEFT JOIN sale_payment sp ON sp.sale_id = s.id
             WHERE cssc.id = ?
        """;
        RowMapper<ElectronicReceiptPrintableResponse> mapper = (rs, rowNum) -> ElectronicReceiptPrintableResponse.builder()
                .saleId(rs.getLong("sale_id"))
                .createdBy((Long) rs.getObject("created_by"))
                .docType(rs.getString("doc_type"))
                .series(rs.getString("series"))
                .number((Long) rs.getObject("number"))
                .issueDate(rs.getDate("issue_date") != null ? rs.getDate("issue_date").toLocalDate() : null)
                .currency(rs.getString("currency"))
                .customerDocType(rs.getString("customer_doc_type"))
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .customerAddress(rs.getString("customer_address"))
                .taxStatus(rs.getString("tax_status"))
                .paymentType(rs.getString("payment_type"))
                .dueDate(rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null)
                .subtotal(rs.getBigDecimal("subtotal"))
                .discountTotal(rs.getBigDecimal("discount_total"))
                .igvAmount(rs.getBigDecimal("igv_amount"))
                .total(rs.getBigDecimal("total"))
                .payment(rs.getString("payment_method") == null ? null : ElectronicReceiptPrintablePaymentResponse.builder()
                        .method(rs.getString("payment_method"))
                        .amount(rs.getBigDecimal("payment_amount"))
                        .paidAt(rs.getTimestamp("payment_paid_at") != null ? rs.getTimestamp("payment_paid_at").toLocalDateTime() : null)
                        .build())
                .build();
        return jdbcClient.sql(sql).param(comboId).query(mapper).optional().orElse(null);
    }

    @Override
    public List<ElectronicReceiptPrintableItemResponse> findElectronicReceiptPrintableItemsBySaleId(Long saleId) {
        String sql = """
            SELECT si.id AS sale_item_id,
                   si.line_number AS line_number,
                   si.product_id AS product_id,
                   si.sku AS sku,
                   si.description AS description,
                   si.presentation AS presentation,
                   si.quantity AS quantity,
                   si.unit_price AS unit_price,
                   si.discount_amount AS discount_amount,
                   si.revenue_total AS revenue_total
              FROM sale_item si
             WHERE si.sale_id = ?
             ORDER BY si.line_number ASC
        """;
        RowMapper<ElectronicReceiptPrintableItemResponse> mapper = (rs, rowNum) -> ElectronicReceiptPrintableItemResponse.builder()
                .saleItemId(rs.getLong("sale_item_id"))
                .lineNumber(rs.getInt("line_number"))
                .productId(rs.getLong("product_id"))
                .sku(rs.getString("sku"))
                .description(rs.getString("description"))
                .presentation(rs.getString("presentation"))
                .quantity(rs.getBigDecimal("quantity"))
                .unitPrice(rs.getBigDecimal("unit_price"))
                .discountAmount(rs.getBigDecimal("discount_amount"))
                .revenueTotal(rs.getBigDecimal("revenue_total"))
                .build();
        return jdbcClient.sql(sql).param(saleId).query(mapper).list();
    }

}
