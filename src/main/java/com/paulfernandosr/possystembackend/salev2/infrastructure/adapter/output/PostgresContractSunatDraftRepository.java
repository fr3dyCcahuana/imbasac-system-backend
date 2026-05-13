package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ContractSunatDraftRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftItemResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresContractSunatDraftRepository implements ContractSunatDraftRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ContractBase findContractBase(Long contractId) {
        String sql = """
            SELECT
                c.id AS contract_id,
                c.sale_id,
                c.status AS contract_status,
                c.payment_type,
                c.issue_date,
                CASE WHEN UPPER(COALESCE(c.customer_doc_type, '')) = 'RUC' THEN 'FACTURA' ELSE 'BOLETA' END AS doc_type,
                CASE WHEN UPPER(COALESCE(c.customer_doc_type, '')) = 'RUC' THEN 'F004' ELSE 'B004' END AS series,
                c.currency,
                c.exchange_rate,
                c.price_list,
                c.customer_id,
                c.customer_doc_type,
                c.customer_doc_number,
                c.customer_name,
                c.customer_address,
                COALESCE(cu.ubigeo, ca.ubigeo) AS customer_ubigeo,
                COALESCE(cu.department, ca.department) AS customer_department,
                COALESCE(cu.province, ca.province) AS customer_province,
                COALESCE(cu.district, ca.district) AS customer_district,
                c.cash_price
            FROM contract c
            LEFT JOIN customers cu ON cu.id = c.customer_id
            LEFT JOIN LATERAL (
                SELECT address, ubigeo, department, province, district
                FROM customer_address
                WHERE customer_id = c.customer_id
                  AND enabled = TRUE
                ORDER BY fiscal DESC, position ASC, id ASC
                LIMIT 1
            ) ca ON TRUE
            WHERE c.id = ?
        """;

        return jdbcClient.sql(sql)
                .param(contractId)
                .query((rs, rowNum) -> ContractBase.builder()
                        .contractId(rs.getLong("contract_id"))
                        .saleId(rs.getObject("sale_id") != null ? rs.getLong("sale_id") : null)
                        .contractStatus(rs.getString("contract_status"))
                        .paymentType(rs.getString("payment_type"))
                        .issueDate(rs.getObject("issue_date", LocalDate.class))
                        .docType(rs.getString("doc_type"))
                        .series(rs.getString("series"))
                        .currency(rs.getString("currency"))
                        .exchangeRate(rs.getBigDecimal("exchange_rate"))
                        .priceList(rs.getString("price_list"))
                        .customerId(rs.getObject("customer_id") != null ? rs.getLong("customer_id") : null)
                        .customerDocType(rs.getString("customer_doc_type"))
                        .customerDocNumber(rs.getString("customer_doc_number"))
                        .customerName(rs.getString("customer_name"))
                        .customerAddress(rs.getString("customer_address"))
                        .customerUbigeo(rs.getString("customer_ubigeo"))
                        .customerDepartment(rs.getString("customer_department"))
                        .customerProvince(rs.getString("customer_province"))
                        .customerDistrict(rs.getString("customer_district"))
                        .cashPrice(rs.getBigDecimal("cash_price"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public ContractSunatDraftResponse findByContractId(Long contractId) {
        ContractSunatDraftResponse header = findHeaderByContractId(contractId, false);
        if (header == null) return null;
        header.setItems(findItemsByDraftId(header.getId()));
        return header;
    }

    @Override
    public ContractSunatDraftResponse createFromContract(Long contractId, Long userId) {
        ContractSunatDraftResponse existing = findByContractId(contractId);
        if (existing != null) return existing;

        ContractBase base = findContractBase(contractId);
        if (base == null) throw new InvalidSaleV2Exception("Contrato no encontrado: " + contractId);
        if (base.getSaleId() != null) throw new InvalidSaleV2Exception("El contrato ya tiene venta asociada: " + base.getSaleId());

        String insertHeader = """
            INSERT INTO contract_sunat_draft (
                contract_id,
                sale_id,
                doc_type,
                series,
                number,
                issue_date,
                billing_customer_id,
                billing_doc_type,
                billing_doc_number,
                billing_name,
                billing_address,
                billing_ubigeo,
                billing_department,
                billing_province,
                billing_district,
                payment_method,
                tax_status,
                tax_reason,
                igv_rate,
                igv_included,
                subtotal,
                discount_total,
                igv_amount,
                total,
                status,
                sunat_status,
                created_by,
                updated_by
            ) VALUES (
                ?, NULL,
                ?, ?, NULL, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                NULL,
                'NO_GRAVADA', '20', 18.00, FALSE,
                0, 0, 0, 0,
                'BORRADOR', 'NO_ENVIADO',
                ?, ?
            )
            RETURNING id
        """;

        Long draftId = jdbcClient.sql(insertHeader)
                .params(
                        contractId,
                        base.getDocType(), base.getSeries(), base.getIssueDate() != null ? base.getIssueDate() : LocalDate.now(),
                        base.getCustomerId(), base.getCustomerDocType(), base.getCustomerDocNumber(), base.getCustomerName(), base.getCustomerAddress(),
                        base.getCustomerUbigeo(), base.getCustomerDepartment(), base.getCustomerProvince(), base.getCustomerDistrict(),
                        userId, userId
                )
                .query(Long.class)
                .single();

        String insertItems = """
            INSERT INTO contract_sunat_draft_item (
                draft_id,
                line_number,
                contract_item_id,
                product_id,
                serial_unit_id,
                sku,
                description,
                quantity,
                original_unit_price,
                sunat_unit_price,
                original_revenue_total,
                sunat_revenue_total
            )
            SELECT
                ?,
                1,
                ci.id,
                ci.product_id,
                ci.serial_unit_id,
                ci.sku,
                ci.description,
                1,
                ci.unit_price,
                ci.unit_price,
                ci.unit_price,
                ci.unit_price
            FROM contract_item ci
            WHERE ci.contract_id = ?
        """;

        int insertedItems = jdbcClient.sql(insertItems)
                .params(draftId, contractId)
                .update();

        if (insertedItems == 0) {
            throw new InvalidSaleV2Exception("Contrato no tiene ítems para SUNAT: " + contractId);
        }

        recalculateDraftTotals(draftId);
        return findByContractId(contractId);
    }

    @Override
    public ContractSunatDraftResponse saveDraft(Long contractId, ContractSunatDraftSaveRequest request, Long userId) {
        ContractSunatDraftResponse draft = findByContractId(contractId);
        if (draft == null) draft = createFromContract(contractId, userId);

        if (!"BORRADOR".equalsIgnoreCase(nz(draft.getStatus()))) {
            throw new InvalidSaleV2Exception("El borrador SUNAT ya no puede editarse. Estado: " + draft.getStatus());
        }
        if (draft.getNumber() != null) {
            throw new InvalidSaleV2Exception("El borrador ya tiene número asignado. No se puede modificar.");
        }

        String docType = upper(request.getDocType());
        String taxStatus = upper(request.getTaxStatus());
        if (!"BOLETA".equals(docType) && !"FACTURA".equals(docType)) {
            throw new InvalidSaleV2Exception("docType inválido para SUNAT: " + request.getDocType());
        }
        if (!"GRAVADA".equals(taxStatus) && !"NO_GRAVADA".equals(taxStatus)) {
            throw new InvalidSaleV2Exception("taxStatus inválido: " + request.getTaxStatus());
        }

        jdbcClient.sql("""
            UPDATE contract_sunat_draft
               SET doc_type = ?,
                   series = ?,
                   issue_date = ?,
                   billing_customer_id = ?,
                   billing_doc_type = ?,
                   billing_doc_number = ?,
                   billing_name = ?,
                   billing_address = ?,
                   billing_ubigeo = ?,
                   billing_department = ?,
                   billing_province = ?,
                   billing_district = ?,
                   payment_method = ?,
                   tax_status = ?,
                   tax_reason = ?,
                   igv_rate = ?,
                   igv_included = ?,
                   edit_reason = ?,
                   updated_by = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(
                        docType,
                        request.getSeries().trim().toUpperCase(),
                        request.getIssueDate(),
                        request.getBillingCustomerId(),
                        trimToNull(request.getBillingDocType()),
                        trimToNull(request.getBillingDocNumber()),
                        trimToNull(request.getBillingName()),
                        trimToNull(request.getBillingAddress()),
                        trimToNull(request.getBillingUbigeo()),
                        trimToNull(request.getBillingDepartment()),
                        trimToNull(request.getBillingProvince()),
                        trimToNull(request.getBillingDistrict()),
                        trimToNull(request.getPaymentMethod()) == null ? null : trimToNull(request.getPaymentMethod()).toUpperCase(),
                        taxStatus,
                        "NO_GRAVADA".equals(taxStatus) ? "20" : null,
                        nz(request.getIgvRate(), new BigDecimal("18.00")),
                        "GRAVADA".equals(taxStatus) && Boolean.TRUE.equals(request.getIgvIncluded()),
                        request.getEditReason().trim(),
                        userId,
                        draft.getId()
                )
                .update();

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidSaleV2Exception("El borrador debe tener al menos un item.");
        }

        for (ContractSunatDraftSaveRequest.Item item : request.getItems()) {
            if (item.getLineNumber() == null) {
                throw new InvalidSaleV2Exception("lineNumber es obligatorio en cada item del nuevo flujo SUNAT por contrato.");
            }
            if (item.getSunatUnitPrice() == null || item.getSunatUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("sunatUnitPrice inválido para lineNumber=" + item.getLineNumber());
            }

            int updated = jdbcClient.sql("""
                UPDATE contract_sunat_draft_item
                   SET sunat_unit_price = ?,
                       sunat_revenue_total = quantity * ?
                 WHERE draft_id = ?
                   AND line_number = ?
            """)
                    .params(item.getSunatUnitPrice(), item.getSunatUnitPrice(), draft.getId(), item.getLineNumber())
                    .update();

            if (updated != 1) {
                throw new InvalidSaleV2Exception("El item lineNumber=" + item.getLineNumber() + " no pertenece al borrador SUNAT.");
            }
        }

        recalculateDraftTotals(draft.getId());
        return findByContractId(contractId);
    }

    @Override
    public ContractSunatDraftResponse lockDraftForEmission(Long contractId) {
        ContractSunatDraftResponse header = findHeaderByContractId(contractId, true);
        if (header == null) return null;
        header.setItems(findItemsByDraftId(header.getId()));
        return header;
    }

    @Override
    public void setDraftNumberAndStatus(Long draftId, Long number, String status) {
        jdbcClient.sql("""
            UPDATE contract_sunat_draft
               SET number = ?,
                   status = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(number, status, draftId)
                .update();
    }

    @Override
    public void markDraftEmissionResult(Long draftId,
                                        Long saleId,
                                        String sunatStatus,
                                        String sunatCode,
                                        String sunatDescription,
                                        String hashCode,
                                        String xmlPath,
                                        String cdrPath,
                                        String pdfPath,
                                        LocalDateTime emittedAt,
                                        String draftStatus) {
        jdbcClient.sql("""
            UPDATE contract_sunat_draft
               SET sale_id = COALESCE(?, sale_id),
                   sunat_status = ?,
                   sunat_response_code = ?,
                   sunat_response_description = ?,
                   sunat_hash_code = ?,
                   sunat_xml_path = ?,
                   sunat_cdr_path = ?,
                   sunat_pdf_path = ?,
                   sunat_sent_at = ?,
                   status = COALESCE(?, status),
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(saleId, sunatStatus, sunatCode, sunatDescription, hashCode, xmlPath, cdrPath, pdfPath, emittedAt, draftStatus, draftId)
                .update();
    }

    @Override
    public List<DraftItemForSunat> findDraftItemsForSunat(Long draftId) {
        String sql = """
            SELECT
                di.id AS draft_item_id,
                di.line_number,
                di.product_id,
                di.sku,
                di.description,
                p.presentation,
                p.factor,
                p.category AS product_category,
                di.quantity,
                di.sunat_unit_price AS unit_price,
                di.sunat_revenue_total AS revenue_total,
                COALESCE(p.facturable_sunat, TRUE) AS facturable_sunat,
                COALESCE(p.affects_stock, TRUE) AS affects_stock,
                COALESCE(p.manage_by_serial, FALSE) AS manage_by_serial,
                'VENDIDO' AS line_kind,
                TRUE AS visible_in_document,

                psu.id AS serial_unit_id,
                psu.vin AS vin,
                psu.chassis_number AS chassis_number,
                psu.engine_number AS engine_number,
                psu.color AS color,
                psu.year_make AS year_make,
                psu.dua_number AS dua_number,
                psu.dua_item AS dua_item,

                p.brand AS brand,
                p.model AS model,

                vs.vehicle_type AS vehicle_type,
                vs.bodywork AS bodywork,
                vs.engine_capacity AS engine_capacity,
                vs.fuel AS fuel,
                vs.cylinders AS cylinders,
                vs.net_weight AS net_weight,
                vs.payload AS payload,
                vs.gross_weight AS gross_weight,
                vs.vehicle_class AS vehicle_class,
                vs.engine_power AS engine_power,
                vs.rolling_form AS rolling_form,
                vs.seats AS seats,
                vs.passengers AS passengers,
                vs.axles AS axles,
                vs.wheels AS wheels,
                vs.length AS length,
                vs.width AS width,
                vs.height AS height
            FROM contract_sunat_draft_item di
            JOIN contract_sunat_draft d ON d.id = di.draft_id
            LEFT JOIN product p ON p.id = di.product_id
            LEFT JOIN product_serial_unit psu ON psu.id = di.serial_unit_id
            LEFT JOIN product_vehicle_specs vs ON vs.product_id = p.id
            WHERE di.draft_id = ?
            ORDER BY di.line_number
        """;

        return jdbcClient.sql(sql)
                .param(draftId)
                .query((rs, rowNum) -> DraftItemForSunat.builder()
                        .draftItemId(rs.getLong("draft_item_id"))
                        .lineNumber(rs.getInt("line_number"))
                        .productId(rs.getLong("product_id"))
                        .sku(rs.getString("sku"))
                        .description(rs.getString("description"))
                        .presentation(rs.getString("presentation"))
                        .factor(rs.getBigDecimal("factor"))
                        .productCategory(rs.getString("product_category"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .unitPrice(rs.getBigDecimal("unit_price"))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
                        .facturableSunat(rs.getBoolean("facturable_sunat"))
                        .affectsStock(rs.getBoolean("affects_stock"))
                        .manageBySerial(rs.getBoolean("manage_by_serial"))
                        .lineKind(rs.getString("line_kind"))
                        .visibleInDocument(rs.getBoolean("visible_in_document"))
                        .serialUnitId(rs.getObject("serial_unit_id") != null ? rs.getLong("serial_unit_id") : null)
                        .vin(rs.getString("vin"))
                        .chassisNumber(rs.getString("chassis_number"))
                        .engineNumber(rs.getString("engine_number"))
                        .color(rs.getString("color"))
                        .yearMake(rs.getObject("year_make", Integer.class))
                        .duaNumber(rs.getString("dua_number"))
                        .duaItem(rs.getObject("dua_item", Integer.class))
                        .brand(rs.getString("brand"))
                        .model(rs.getString("model"))
                        .vehicleType(rs.getString("vehicle_type"))
                        .bodywork(rs.getString("bodywork"))
                        .engineCapacity(rs.getString("engine_capacity"))
                        .fuel(rs.getString("fuel"))
                        .cylinders(rs.getObject("cylinders", Integer.class))
                        .netWeight(rs.getBigDecimal("net_weight"))
                        .payload(rs.getBigDecimal("payload"))
                        .grossWeight(rs.getBigDecimal("gross_weight"))
                        .vehicleClass(rs.getString("vehicle_class"))
                        .enginePower(rs.getString("engine_power"))
                        .rollingForm(rs.getString("rolling_form"))
                        .seats(rs.getObject("seats", Integer.class))
                        .passengers(rs.getObject("passengers", Integer.class))
                        .axles(rs.getObject("axles", Integer.class))
                        .wheels(rs.getObject("wheels", Integer.class))
                        .length(rs.getBigDecimal("length"))
                        .width(rs.getBigDecimal("width"))
                        .height(rs.getBigDecimal("height"))
                        .build())
                .list();
    }

    @Override
    public Long createSaleFromDraft(Long draftId, Long userId, Long number, String notes) {
        String sql = """
            INSERT INTO sale(
              station_id,
              sale_session_id,
              created_by,
              doc_type,
              series,
              number,
              issue_date,
              currency,
              exchange_rate,
              price_list,
              customer_id,
              customer_doc_type,
              customer_doc_number,
              customer_name,
              customer_address,
              customer_ubigeo,
              customer_department,
              customer_province,
              customer_district,
              tax_status,
              tax_reason,
              igv_rate,
              igv_included,
              payment_type,
              credit_days,
              due_date,
              subtotal,
              discount_total,
              igv_amount,
              total,
              gift_cost_total,
              notes,
              status,
              sunat_status,
              contract_id,
              created_at,
              updated_at
            )
            SELECT
              c.station_id,
              NULL,
              ?,
              d.doc_type,
              d.series,
              ?,
              d.issue_date,
              COALESCE(c.currency, 'PEN'),
              c.exchange_rate,
              c.price_list,
              d.billing_customer_id,
              d.billing_doc_type,
              d.billing_doc_number,
              d.billing_name,
              d.billing_address,
              d.billing_ubigeo,
              d.billing_department,
              d.billing_province,
              d.billing_district,
              d.tax_status,
              d.tax_reason,
              d.igv_rate,
              d.igv_included,
              'CONTADO',
              NULL,
              NULL,
              d.subtotal,
              d.discount_total,
              d.igv_amount,
              d.total,
              0,
              ?,
              'EMITIDA',
              'NO_ENVIADO',
              d.contract_id,
              NOW(),
              NOW()
            FROM contract_sunat_draft d
            JOIN contract c ON c.id = d.contract_id
            WHERE d.id = ?
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(userId, number, notes, draftId)
                .query(Long.class)
                .single();
    }

    @Override
    public Long createSaleItemFromDraftLine(Long saleId,
                                            DraftItemForSunat item,
                                            BigDecimal unitCostSnapshot,
                                            BigDecimal totalCostSnapshot) {
        String sql = """
            INSERT INTO sale_item(
              sale_id,
              line_number,
              product_id,
              sku,
              description,
              presentation,
              factor,
              quantity,
              unit_price,
              discount_percent,
              discount_amount,
              line_kind,
              gift_reason,
              facturable_sunat,
              affects_stock,
              visible_in_document,
              unit_cost_snapshot,
              total_cost_snapshot,
              revenue_total,
              created_at
            ) VALUES (
              ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'VENDIDO', NULL, ?, ?, TRUE, ?, ?, ?, NOW()
            )
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(
                        saleId,
                        item.getLineNumber(),
                        item.getProductId(),
                        item.getSku(),
                        item.getDescription(),
                        item.getPresentation(),
                        item.getFactor(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        Boolean.TRUE.equals(item.getFacturableSunat()),
                        Boolean.TRUE.equals(item.getAffectsStock()),
                        unitCostSnapshot,
                        totalCostSnapshot,
                        item.getRevenueTotal()
                )
                .query(Long.class)
                .single();
    }

    @Override
    public void updateSaleTotalsFromDraft(Long saleId, Long draftId) {
        jdbcClient.sql("""
            UPDATE sale s
               SET subtotal = d.subtotal,
                   discount_total = d.discount_total,
                   igv_amount = d.igv_amount,
                   total = d.total,
                   updated_at = NOW()
              FROM contract_sunat_draft d
             WHERE s.id = ?
               AND d.id = ?
        """)
                .params(saleId, draftId)
                .update();
    }

    @Override
    public void linkDraftToSale(Long draftId, Long saleId) {
        jdbcClient.sql("""
            UPDATE contract_sunat_draft
               SET sale_id = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(saleId, draftId)
                .update();
    }

    @Override
    public void linkContractToSale(Long contractId, Long saleId) {
        jdbcClient.sql("""
            UPDATE contract
               SET sale_id = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(saleId, contractId)
                .update();
    }

    @Override
    public void markSaleEmissionResult(Long saleId,
                                       String sunatStatus,
                                       String sunatCode,
                                       String sunatDescription,
                                       String hashCode,
                                       String xmlPath,
                                       String cdrPath,
                                       String pdfPath,
                                       LocalDateTime emittedAt) {
        jdbcClient.sql("""
            UPDATE sale
               SET sunat_status = ?,
                   sunat_response_code = ?,
                   sunat_response_description = ?,
                   sunat_hash_code = ?,
                   sunat_xml_path = ?,
                   sunat_cdr_path = ?,
                   sunat_pdf_path = ?,
                   sunat_sent_at = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(sunatStatus, sunatCode, sunatDescription, hashCode, xmlPath, cdrPath, pdfPath, emittedAt, saleId)
                .update();
    }

    private ContractSunatDraftResponse findHeaderByContractId(Long contractId, boolean forUpdate) {
        String sql = """
            SELECT
                d.id,
                d.sale_id,
                d.contract_id,
                d.doc_type,
                d.series,
                d.number,
                d.issue_date,
                d.billing_customer_id,
                d.billing_doc_type,
                d.billing_doc_number,
                d.billing_name,
                d.billing_address,
                d.billing_ubigeo,
                d.billing_department,
                d.billing_province,
                d.billing_district,
                d.payment_method,
                d.tax_status,
                d.tax_reason,
                d.igv_rate,
                d.igv_included,
                d.subtotal,
                d.discount_total,
                d.igv_amount,
                d.total,
                d.status,
                d.edit_reason,
                d.sunat_status
            FROM contract_sunat_draft d
            WHERE d.contract_id = ?
        """ + (forUpdate ? " FOR UPDATE" : "");

        return jdbcClient.sql(sql)
                .param(contractId)
                .query(this::mapHeader)
                .optional()
                .orElse(null);
    }

    private List<ContractSunatDraftItemResponse> findItemsByDraftId(Long draftId) {
        String sql = """
            SELECT
                id,
                line_number,
                product_id,
                sku,
                description,
                quantity,
                original_unit_price,
                sunat_unit_price,
                original_revenue_total,
                sunat_revenue_total
            FROM contract_sunat_draft_item
            WHERE draft_id = ?
            ORDER BY line_number
        """;

        return jdbcClient.sql(sql)
                .param(draftId)
                .query((rs, rowNum) -> ContractSunatDraftItemResponse.builder()
                        .id(rs.getLong("id"))
                        .saleItemId(null)
                        .lineNumber(rs.getInt("line_number"))
                        .productId(rs.getLong("product_id"))
                        .sku(rs.getString("sku"))
                        .description(rs.getString("description"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .originalUnitPrice(rs.getBigDecimal("original_unit_price"))
                        .sunatUnitPrice(rs.getBigDecimal("sunat_unit_price"))
                        .originalRevenueTotal(rs.getBigDecimal("original_revenue_total"))
                        .sunatRevenueTotal(rs.getBigDecimal("sunat_revenue_total"))
                        .build())
                .list();
    }

    private ContractSunatDraftResponse mapHeader(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return ContractSunatDraftResponse.builder()
                .id(rs.getLong("id"))
                .saleId(rs.getObject("sale_id") != null ? rs.getLong("sale_id") : null)
                .contractId(rs.getLong("contract_id"))
                .docType(rs.getString("doc_type"))
                .series(rs.getString("series"))
                .number(rs.getObject("number") != null ? rs.getLong("number") : null)
                .issueDate(rs.getDate("issue_date") != null ? rs.getDate("issue_date").toLocalDate() : null)
                .billingCustomerId(rs.getObject("billing_customer_id") != null ? rs.getLong("billing_customer_id") : null)
                .customerDocType(rs.getString("billing_doc_type"))
                .customerDocNumber(rs.getString("billing_doc_number"))
                .customerName(rs.getString("billing_name"))
                .customerAddress(rs.getString("billing_address"))
                .customerUbigeo(rs.getString("billing_ubigeo"))
                .customerDepartment(rs.getString("billing_department"))
                .customerProvince(rs.getString("billing_province"))
                .customerDistrict(rs.getString("billing_district"))
                .paymentMethod(rs.getString("payment_method"))
                .taxStatus(rs.getString("tax_status"))
                .taxReason(rs.getString("tax_reason"))
                .igvRate(rs.getBigDecimal("igv_rate"))
                .igvIncluded(rs.getBoolean("igv_included"))
                .subtotal(rs.getBigDecimal("subtotal"))
                .discountTotal(rs.getBigDecimal("discount_total"))
                .igvAmount(rs.getBigDecimal("igv_amount"))
                .total(rs.getBigDecimal("total"))
                .status(rs.getString("status"))
                .editReason(rs.getString("edit_reason"))
                .sunatStatus(rs.getString("sunat_status"))
                .build();
    }

    private void recalculateDraftTotals(Long draftId) {
        ContractSunatDraftResponse header = jdbcClient.sql("""
            SELECT tax_status, igv_rate, igv_included
            FROM contract_sunat_draft
            WHERE id = ?
        """)
                .param(draftId)
                .query((rs, rowNum) -> ContractSunatDraftResponse.builder()
                        .taxStatus(rs.getString("tax_status"))
                        .igvRate(rs.getBigDecimal("igv_rate"))
                        .igvIncluded(rs.getBoolean("igv_included"))
                        .build())
                .single();

        BigDecimal gross = jdbcClient.sql("""
            SELECT COALESCE(SUM(sunat_revenue_total), 0)
            FROM contract_sunat_draft_item
            WHERE draft_id = ?
        """)
                .param(draftId)
                .query(BigDecimal.class)
                .single();

        BigDecimal rate = nz(header.getIgvRate(), new BigDecimal("18.00"))
                .divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP);

        boolean gravada = "GRAVADA".equalsIgnoreCase(header.getTaxStatus());
        boolean igvIncluded = Boolean.TRUE.equals(header.getIgvIncluded());

        BigDecimal subtotal;
        BigDecimal igv;
        BigDecimal total;

        if (!gravada) {
            subtotal = gross;
            igv = BigDecimal.ZERO;
            total = gross;
        } else if (igvIncluded) {
            subtotal = gross.divide(BigDecimal.ONE.add(rate), 4, java.math.RoundingMode.HALF_UP);
            igv = gross.subtract(subtotal);
            total = gross;
        } else {
            subtotal = gross;
            igv = subtotal.multiply(rate);
            total = subtotal.add(igv);
        }

        jdbcClient.sql("""
            UPDATE contract_sunat_draft
               SET subtotal = ?,
                   discount_total = 0,
                   igv_amount = ?,
                   total = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(
                        subtotal.setScale(4, java.math.RoundingMode.HALF_UP),
                        igv.setScale(4, java.math.RoundingMode.HALF_UP),
                        total.setScale(4, java.math.RoundingMode.HALF_UP),
                        draftId
                )
                .update();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static BigDecimal nz(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
