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
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresContractSunatDraftRepository implements ContractSunatDraftRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ContractSaleBase findContractSaleBase(Long saleId) {
        String sql = """
            SELECT
                s.id AS sale_id,
                s.contract_id AS contract_id,
                s.status AS sale_status,
                s.doc_type AS sale_doc_type,
                s.sunat_status AS sunat_status,
                d.status AS draft_status
            FROM sale s
            LEFT JOIN sale_contract_sunat_draft d ON d.sale_id = s.id
            WHERE s.id = ?
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> ContractSaleBase.builder()
                        .saleId(rs.getLong("sale_id"))
                        .contractId(rs.getObject("contract_id") != null ? rs.getLong("contract_id") : null)
                        .saleStatus(rs.getString("sale_status"))
                        .saleDocType(rs.getString("sale_doc_type"))
                        .sunatStatus(rs.getString("sunat_status"))
                        .draftStatus(rs.getString("draft_status"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public ContractSunatDraftResponse findBySaleId(Long saleId) {
        ContractSunatDraftResponse header = findHeaderBySaleId(saleId);
        if (header == null) return null;

        header.setItems(findItemsByDraftId(header.getId()));
        return header;
    }

    @Override
    public ContractSunatDraftResponse createFromSale(Long saleId, Long userId) {
        ContractSaleBase base = findContractSaleBase(saleId);

        if (base == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        if (base.getContractId() == null) {
            throw new InvalidSaleV2Exception("La venta no está asociada a un contrato.");
        }

        if (!"EMITIDA".equalsIgnoreCase(nz(base.getSaleStatus()))) {
            throw new InvalidSaleV2Exception("Solo se puede crear borrador SUNAT para venta EMITIDA.");
        }

        ContractSunatDraftResponse existing = findBySaleId(saleId);
        if (existing != null) {
            return existing;
        }

        String insertHeader = """
            INSERT INTO sale_contract_sunat_draft (
                sale_id,
                contract_id,
                doc_type,
                series,
                number,
                issue_date,
                customer_doc_type,
                customer_doc_number,
                customer_name,
                customer_address,
                tax_status,
                tax_reason,
                igv_rate,
                igv_included,
                subtotal,
                discount_total,
                igv_amount,
                total,
                status,
                created_by,
                updated_by
            )
            SELECT
                s.id,
                s.contract_id,
                s.doc_type,
                s.series,
                s.number,
                s.issue_date,
                s.customer_doc_type,
                s.customer_doc_number,
                s.customer_name,
                s.customer_address,
                s.tax_status,
                CASE WHEN s.tax_status = 'NO_GRAVADA' THEN '20' ELSE s.tax_reason END,
                s.igv_rate,
                s.igv_included,
                s.subtotal,
                s.discount_total,
                s.igv_amount,
                s.total,
                'BORRADOR',
                ?,
                ?
            FROM sale s
            WHERE s.id = ?
            RETURNING id
        """;

        Long draftId = jdbcClient.sql(insertHeader)
                .params(userId, userId, saleId)
                .query(Long.class)
                .single();

        String insertItems = """
            INSERT INTO sale_contract_sunat_draft_item (
                draft_id,
                sale_item_id,
                line_number,
                product_id,
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
                si.id,
                si.line_number,
                si.product_id,
                si.sku,
                si.description,
                si.quantity,
                si.unit_price,
                si.unit_price,
                si.revenue_total,
                si.revenue_total
            FROM sale_item si
            WHERE si.sale_id = ?
            ORDER BY si.line_number
        """;

        jdbcClient.sql(insertItems)
                .params(draftId, saleId)
                .update();

        recalculateDraftTotals(draftId);

        return findBySaleId(saleId);
    }

    @Override
    public ContractSunatDraftResponse saveDraft(Long saleId, ContractSunatDraftSaveRequest request, Long userId) {
        ContractSunatDraftResponse draft = findBySaleId(saleId);

        if (draft == null) {
            draft = createFromSale(saleId, userId);
        }

        if (!"BORRADOR".equalsIgnoreCase(nz(draft.getStatus()))) {
            throw new InvalidSaleV2Exception("El borrador SUNAT ya no puede editarse. Estado: " + draft.getStatus());
        }

        String docType = upper(request.getDocType());
        String taxStatus = upper(request.getTaxStatus());

        if (!"BOLETA".equals(docType) && !"FACTURA".equals(docType)) {
            throw new InvalidSaleV2Exception("docType inválido para SUNAT: " + request.getDocType());
        }

        if (!"GRAVADA".equals(taxStatus) && !"NO_GRAVADA".equals(taxStatus)) {
            throw new InvalidSaleV2Exception("taxStatus inválido: " + request.getTaxStatus());
        }

        if (request.getIssueDate() == null) {
            throw new InvalidSaleV2Exception("issueDate es obligatorio.");
        }

        if (request.getSeries() == null || request.getSeries().isBlank()) {
            throw new InvalidSaleV2Exception("series es obligatorio.");
        }

        if (request.getEditReason() == null || request.getEditReason().trim().length() < 5) {
            throw new InvalidSaleV2Exception("editReason es obligatorio y debe tener al menos 5 caracteres.");
        }

        jdbcClient.sql("""
            UPDATE sale_contract_sunat_draft
               SET doc_type = ?,
                   series = ?,
                   issue_date = ?,
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
            if (item.getSaleItemId() == null) {
                throw new InvalidSaleV2Exception("saleItemId es obligatorio en cada item.");
            }

            if (item.getSunatUnitPrice() == null || item.getSunatUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("sunatUnitPrice inválido para saleItemId=" + item.getSaleItemId());
            }

            int updated = jdbcClient.sql("""
                UPDATE sale_contract_sunat_draft_item
                   SET sunat_unit_price = ?,
                       sunat_revenue_total = quantity * ?
                 WHERE draft_id = ?
                   AND sale_item_id = ?
            """)
                    .params(item.getSunatUnitPrice(), item.getSunatUnitPrice(), draft.getId(), item.getSaleItemId())
                    .update();

            if (updated != 1) {
                throw new InvalidSaleV2Exception("El item saleItemId=" + item.getSaleItemId() + " no pertenece al borrador SUNAT.");
            }
        }

        recalculateDraftTotals(draft.getId());

        return findBySaleId(saleId);
    }

    @Override
    public ContractSunatDraftResponse lockDraftForEmission(Long saleId) {
        String sql = """
            SELECT
                d.id,
                d.sale_id,
                d.contract_id,
                d.doc_type,
                d.series,
                d.number,
                d.issue_date,
                d.customer_doc_type,
                d.customer_doc_number,
                d.customer_name,
                d.customer_address,
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
                s.sunat_status
            FROM sale_contract_sunat_draft d
            JOIN sale s ON s.id = d.sale_id
            WHERE d.sale_id = ?
            FOR UPDATE OF d
        """;

        ContractSunatDraftResponse header = jdbcClient.sql(sql)
                .param(saleId)
                .query(this::mapHeader)
                .optional()
                .orElse(null);

        if (header == null) return null;

        header.setItems(findItemsByDraftId(header.getId()));
        return header;
    }

    @Override
    public void setDraftNumberAndStatus(Long draftId, Long number, String status) {
        jdbcClient.sql("""
            UPDATE sale_contract_sunat_draft
               SET number = ?,
                   status = ?,
                   updated_at = NOW()
             WHERE id = ?
        """)
                .params(number, status, draftId)
                .update();
    }

    @Override
    public void markSaleEmissionResult(
            Long saleId,
            String sunatStatus,
            String sunatCode,
            String sunatDescription,
            String hashCode,
            String xmlPath,
            String cdrPath,
            String pdfPath,
            LocalDateTime emittedAt
    ) {
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
                .params(
                        sunatStatus,
                        sunatCode,
                        sunatDescription,
                        hashCode,
                        xmlPath,
                        cdrPath,
                        pdfPath,
                        emittedAt,
                        saleId
                )
                .update();
    }

    @Override
    public void markSaleEmissionError(Long saleId, String description, LocalDateTime emittedAt) {
        markSaleEmissionResult(saleId, "ERROR", null, description, null, null, null, null, emittedAt);
    }

    @Override
    public List<DraftItemForSunat> findDraftItemsForSunat(Long draftId) {
        String sql = """
            SELECT
                di.line_number,
                di.product_id,
                di.sku,
                di.description,
                p.category AS product_category,
                di.quantity,
                di.sunat_revenue_total AS revenue_total,
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
            FROM sale_contract_sunat_draft_item di
            JOIN sale_contract_sunat_draft d ON d.id = di.draft_id
            LEFT JOIN product p ON p.id = di.product_id
            LEFT JOIN contract_item ci
                   ON ci.contract_id = d.contract_id
                  AND ci.product_id = di.product_id
            LEFT JOIN product_serial_unit psu
                   ON psu.sale_item_id = di.sale_item_id
                   OR psu.id = ci.serial_unit_id
                   OR psu.contract_id = d.contract_id
            LEFT JOIN product_vehicle_specs vs ON vs.product_id = p.id
            WHERE di.draft_id = ?
            ORDER BY di.line_number
        """;

        return jdbcClient.sql(sql)
                .param(draftId)
                .query((rs, rowNum) -> DraftItemForSunat.builder()
                        .lineNumber(rs.getInt("line_number"))
                        .productId(rs.getLong("product_id"))
                        .sku(rs.getString("sku"))
                        .description(rs.getString("description"))
                        .productCategory(rs.getString("product_category"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
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

    private ContractSunatDraftResponse findHeaderBySaleId(Long saleId) {
        String sql = """
            SELECT
                d.id,
                d.sale_id,
                d.contract_id,
                d.doc_type,
                d.series,
                d.number,
                d.issue_date,
                d.customer_doc_type,
                d.customer_doc_number,
                d.customer_name,
                d.customer_address,
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
                s.sunat_status
            FROM sale_contract_sunat_draft d
            JOIN sale s ON s.id = d.sale_id
            WHERE d.sale_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query(this::mapHeader)
                .optional()
                .orElse(null);
    }

    private List<ContractSunatDraftItemResponse> findItemsByDraftId(Long draftId) {
        String sql = """
            SELECT
                id,
                sale_item_id,
                line_number,
                product_id,
                sku,
                description,
                quantity,
                original_unit_price,
                sunat_unit_price,
                original_revenue_total,
                sunat_revenue_total
            FROM sale_contract_sunat_draft_item
            WHERE draft_id = ?
            ORDER BY line_number
        """;

        return jdbcClient.sql(sql)
                .param(draftId)
                .query((rs, rowNum) -> ContractSunatDraftItemResponse.builder()
                        .id(rs.getLong("id"))
                        .saleItemId(rs.getLong("sale_item_id"))
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
                .saleId(rs.getLong("sale_id"))
                .contractId(rs.getLong("contract_id"))
                .docType(rs.getString("doc_type"))
                .series(rs.getString("series"))
                .number(rs.getObject("number") != null ? rs.getLong("number") : null)
                .issueDate(rs.getDate("issue_date") != null ? rs.getDate("issue_date").toLocalDate() : null)
                .customerDocType(rs.getString("customer_doc_type"))
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .customerAddress(rs.getString("customer_address"))
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
            FROM sale_contract_sunat_draft
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
            FROM sale_contract_sunat_draft_item
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
            UPDATE sale_contract_sunat_draft
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
}
