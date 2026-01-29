package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.QueryMapper;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.PurchaseItem;
import com.paulfernandosr.possystembackend.purchase.domain.exception.DuplicatePurchaseDocumentException;
import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output.mapper.PurchaseItemRowMapper;
import com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.output.mapper.PurchaseRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresPurchaseRepository implements PurchaseRepository {

    private final JdbcClient jdbcClient;

    @Override
    @Transactional
    public Purchase create(Purchase purchase, String username) {

        // 1) Insertar cabecera
        String insertPurchaseSql = """
    INSERT INTO purchase (
      document_type,
      document_series,
      document_number,
      issue_date,
      entry_date,
      due_date,
      currency,
      exchange_rate,
      payment_type,
      credit_days,
      supplier_ruc,
      supplier_business_name,
      supplier_address,
      igv_rate,
      igv_included,
      apply_igv_to_cost,
      discount_type,
      discount_value,
      freight_amount,
      perception_amount,
      subtotal,
      igv_amount,
      total,
      status,
      notes,
      delivery_guide_series,
      delivery_guide_number,
      delivery_guide_company,
      created_by,
      updated_by,
      created_at,
      updated_at
    )
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?, ?, NOW(), NOW())
    RETURNING id
""";
        try {
            Long purchaseId = jdbcClient.sql(insertPurchaseSql)
                    .params(
                            purchase.getDocumentType(),
                            purchase.getDocumentSeries(),
                            purchase.getDocumentNumber(),
                            purchase.getIssueDate(),
                            purchase.getEntryDate(),
                            purchase.getDueDate(),
                            purchase.getCurrency(),
                            purchase.getExchangeRate(),
                            purchase.getPaymentType(),
                            purchase.getCreditDays(),
                            purchase.getSupplierRuc(),
                            purchase.getSupplierBusinessName(),
                            purchase.getSupplierAddress(),
                            purchase.getIgvRate(),
                            purchase.getIgvIncluded(),
                            purchase.getApplyIgvToCost(),
                            purchase.getDiscountType(),
                            purchase.getDiscountValue(),
                            purchase.getFreightAmount(),
                            purchase.getPerceptionAmount(),
                            purchase.getSubtotal(),
                            purchase.getIgvAmount(),
                            purchase.getTotal(),
                            purchase.getStatus(),
                            purchase.getNotes(),
                            purchase.getDeliveryGuideSeries(),
                            purchase.getDeliveryGuideNumber(),
                            purchase.getDeliveryGuideCompany(),
                            username,
                            username
                    )
                    .query(Long.class)
                    .single();

        // 2) Insertar detalle
        if (purchase.getItems() != null && !purchase.getItems().isEmpty()) {
            String insertItemSql = """
                    INSERT INTO purchase_item(
                        purchase_id,
                        line_number,
                        product_id,
                        description,
                        presentation,
                        quantity,
                        unit_cost,
                        discount_percent,
                        discount_amount,
                        igv_rate,
                        igv_amount,
                        freight_allocated,
                        total_cost,
                        lot_code,
                        expiration_date
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """;

            for (PurchaseItem item : purchase.getItems()) {
                Long itemId = jdbcClient.sql(insertItemSql)
                        .params(
                                purchaseId,
                                item.getLineNumber(),
                                item.getProductId(),
                                item.getDescription(),
                                item.getPresentation(),
                                item.getQuantity(),
                                item.getUnitCost(),
                                item.getDiscountPercent(),
                                item.getDiscountAmount(),
                                item.getIgvRate(),
                                item.getIgvAmount(),
                                item.getFreightAllocated(),
                                item.getTotalCost(),
                                item.getLotCode(),
                                item.getExpirationDate()
                        )
                        .query(Long.class)
                        .single();

                item.setId(itemId);
                item.setPurchaseId(purchaseId);
            }
        }

        // 3) Devolver la compra con el ID asignado
            purchase.setId(purchaseId);
            return purchase;
        } catch (DuplicateKeyException ex) {

            String msg = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();

            if (msg != null && msg.contains("ux_purchase_doc")) {
                throw new DuplicatePurchaseDocumentException(
                        "Ya existe una compra registrada con el mismo proveedor y documento ("
                                + purchase.getSupplierRuc() + " - "
                                + purchase.getDocumentType() + " "
                                + purchase.getDocumentSeries() + "-"
                                + purchase.getDocumentNumber() + ")."
                );
            }

            throw ex;

        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    @Override
    public Page<Purchase> findPage(String query, Pageable pageable) {

        String likeParam = QueryMapper.formatAsLikeParam(query);

        String countSql = """
                SELECT COUNT(1)
                FROM purchase
                WHERE supplier_ruc ILIKE ?
                   OR supplier_business_name ILIKE ?
                   OR document_number ILIKE ?
                """;

        long totalElements = jdbcClient.sql(countSql)
                .params(likeParam, likeParam, likeParam)
                .query(Long.class)
                .single();

        int pageNumber = pageable.getNumber();
        int pageSize = pageable.getSize();
        int offset = pageNumber * pageSize;

        String dataSql = """
                SELECT
                    p.id AS purchase_id,
                    p.document_type,
                    p.document_series,
                    p.document_number,
                    p.issue_date,
                    p.entry_date,
                    p.due_date,
                    p.currency,
                    p.payment_type,
                    p.supplier_ruc,
                    p.supplier_business_name,
                    p.igv_rate,
                    p.subtotal,
                    p.igv_amount,
                    p.total,
                    p.status,
                    p.created_by,
                    p.updated_by,
                    p.created_at,
                    p.updated_at
                FROM purchase p
                WHERE p.supplier_ruc ILIKE ?
                   OR p.supplier_business_name ILIKE ?
                   OR p.document_number ILIKE ?
                ORDER BY p.issue_date DESC, p.id DESC
                LIMIT ? OFFSET ?
                """;

        List<Purchase> purchases = jdbcClient.sql(dataSql)
                .params(likeParam, likeParam, likeParam, pageSize, offset)
                .query(new PurchaseRowMapper())
                .list();

        BigDecimal totalPages = BigDecimal.valueOf(totalElements)
                .divide(BigDecimal.valueOf(pageSize), 0, RoundingMode.CEILING);

        return Page.<Purchase>builder()
                .content(purchases)
                .number(pageNumber)
                .size(pageSize)
                .numberOfElements(purchases.size())
                .totalPages(totalPages.intValue())
                .totalElements(totalElements)
                .build();
    }

    @Override
    public Optional<Purchase> findByIdWithItems(Long purchaseId) {

        String headerSql = """
                SELECT
                    p.id AS purchase_id,
                    p.document_type,
                    p.document_series,
                    p.document_number,
                    p.issue_date,
                    p.entry_date,
                    p.due_date,
                    p.currency,
                    p.exchange_rate,
                    p.payment_type,
                    p.credit_days,
                    p.supplier_ruc,
                    p.supplier_business_name,
                    p.supplier_address,
                    p.igv_rate,
                    p.igv_included,
                    p.apply_igv_to_cost,
                    p.discount_type,
                    p.discount_value,
                    p.freight_amount,
                    p.perception_amount,
                    p.subtotal,
                    p.igv_amount,
                    p.total,
                    p.status,
                    p.notes,
                    p.created_by,
                    p.updated_by,
                    p.created_at,
                    p.updated_at,
                    p.delivery_guide_series,
                    p.delivery_guide_number,
                    p.delivery_guide_company
                FROM purchase p
                WHERE p.id = ?
                """;

        List<Purchase> headerList = jdbcClient.sql(headerSql)
                .param(purchaseId)
                .query(rs -> {
                    if (!rs.next()) {
                        return List.<Purchase>of();
                    }
                    Purchase purchase = Purchase.builder()
                            .id(rs.getLong("purchase_id"))
                            .documentType(rs.getString("document_type"))
                            .documentSeries(rs.getString("document_series"))
                            .documentNumber(rs.getString("document_number"))
                            .issueDate(rs.getDate("issue_date").toLocalDate())
                            .entryDate(rs.getDate("entry_date").toLocalDate())
                            .dueDate(rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null)
                            .currency(rs.getString("currency"))
                            .exchangeRate(rs.getBigDecimal("exchange_rate"))
                            .paymentType(rs.getString("payment_type"))
                            // creditDays: conservar NULL y, si es CONTADO, siempre devolver NULL
                            .creditDays(resolveCreditDays(rs.getString("payment_type"), rs.getObject("credit_days", Integer.class)))
                            .supplierRuc(rs.getString("supplier_ruc"))
                            .supplierBusinessName(rs.getString("supplier_business_name"))
                            .supplierAddress(rs.getString("supplier_address"))
                            .igvRate(rs.getBigDecimal("igv_rate"))
                            .igvIncluded(rs.getBoolean("igv_included"))
                            .applyIgvToCost(rs.getBoolean("apply_igv_to_cost"))
                            .discountType(rs.getString("discount_type"))
                            .discountValue(rs.getBigDecimal("discount_value"))
                            .freightAmount(rs.getBigDecimal("freight_amount"))
                            .perceptionAmount(rs.getBigDecimal("perception_amount"))
                            .subtotal(rs.getBigDecimal("subtotal"))
                            .igvAmount(rs.getBigDecimal("igv_amount"))
                            .total(rs.getBigDecimal("total"))
                            .status(rs.getString("status"))
                            .notes(rs.getString("notes"))
                            .createdBy(rs.getString("created_by"))
                            .updatedBy(rs.getString("updated_by"))
                            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                            .deliveryGuideSeries(rs.getString("delivery_guide_series"))
                            .deliveryGuideNumber(rs.getString("delivery_guide_number"))
                            .deliveryGuideCompany(rs.getString("delivery_guide_company"))
                            .build();

                    return List.of(purchase);
                });

        if (headerList.isEmpty()) {
            return Optional.empty();
        }

        Purchase purchase = headerList.get(0);

        String itemsSql = """
                SELECT
                    pi.id AS purchase_item_id,
                    pi.purchase_id,
                    pi.line_number,
                    pi.product_id,
                    pi.description,
                    pi.presentation,
                    pi.quantity,
                    pi.unit_cost,
                    pi.discount_percent,
                    pi.discount_amount,
                    pi.igv_rate,
                    pi.igv_amount,
                    pi.freight_allocated,
                    pi.total_cost,
                    pi.lot_code,
                    pi.expiration_date
                FROM purchase_item pi
                WHERE pi.purchase_id = ?
                ORDER BY pi.line_number
                """;

        List<PurchaseItem> items = jdbcClient.sql(itemsSql)
                .param(purchaseId)
                .query(new PurchaseItemRowMapper())
                .list();

        // Cargar serialUnits (solo si existen) y asociarlos por purchase_item_id.
        // Esto permite respuesta combinada: items normales -> serialUnits = null; items serializados -> lista.
        attachSerialUnits(items);

        purchase.setItems(items);

        return Optional.of(purchase);
    }

    private static Integer resolveCreditDays(String paymentType, Integer creditDays) {
        if (paymentType == null) return creditDays;
        return "CONTADO".equalsIgnoreCase(paymentType) ? null : creditDays;
    }

    private void attachSerialUnits(List<PurchaseItem> items) {
        if (items == null || items.isEmpty()) return;

        List<Long> itemIds = items.stream()
                .map(PurchaseItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (itemIds.isEmpty()) return;

        String in = itemIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = """
                SELECT
                    psu.purchase_item_id,
                    psu.vin,
                    psu.chassis_number,
                    psu.engine_number,
                    psu.color,
                    psu.year_make,
                    psu.dua_number,
                    psu.dua_item
                FROM product_serial_unit psu
                WHERE psu.purchase_item_id IN (""" + in + ")\n" +
                "ORDER BY psu.purchase_item_id, psu.dua_item";

        var map = jdbcClient.sql(sql)
                .params(itemIds.toArray())
                .query(rs -> {
                    java.util.Map<Long, List<com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit>> out = new java.util.HashMap<>();
                    while (rs.next()) {
                        Long purchaseItemId = rs.getLong("purchase_item_id");
                        com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit u = com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit.builder()
                                .vin(rs.getString("vin"))
                                .chassisNumber(rs.getString("chassis_number"))
                                .engineNumber(rs.getString("engine_number"))
                                .color(rs.getString("color"))
                                .yearMake(rs.getObject("year_make", Integer.class))
                                .duaNumber(rs.getString("dua_number"))
                                .duaItem(rs.getObject("dua_item", Integer.class))
                                .build();
                        out.computeIfAbsent(purchaseItemId, k -> new java.util.ArrayList<>()).add(u);
                    }
                    return out;
                });

        for (PurchaseItem item : items) {
            var serials = map.get(item.getId());
            item.setSerialUnits((serials == null || serials.isEmpty()) ? null : serials);
        }
    }

    @Override
    public void updateStatus(Long purchaseId, String status, String username) {
        String sql = """
                UPDATE purchase
                SET status = ?, updated_by = ?, updated_at = NOW()
                WHERE id = ?
                """;

        jdbcClient.sql(sql)
                .params(status, username, purchaseId)
                .update();
    }
}
