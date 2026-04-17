package com.paulfernandosr.possystembackend.countersale.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.EmitCounterSaleSunatCombinationUseCase;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SalePaymentRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2Repository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatCodeInferer;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmitCounterSaleSunatCombinationService implements EmitCounterSaleSunatCombinationUseCase {

    private static final String SUCCESS_RESPONSE = "0";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CounterSaleSunatCombinationComposer composer;
    private final UserRepository userRepository;
    private final com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository saleDocumentSeriesRepository;
    private final SaleV2Repository saleV2Repository;
    private final SalePaymentRepository salePaymentRepository;
    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final ObjectMapper objectMapper;
    private final JdbcClient jdbcClient;

    @Override
    @Transactional(noRollbackFor = Exception.class)
    public CounterSaleSunatCombinationEmitResponse emit(Long anchorCounterSaleId,
                                                        CounterSaleSunatCombinationRequest request,
                                                        String username) {
        CounterSaleSunatCombinationComposer.ComposedResult result = composer.compose(anchorCounterSaleId, request);
        if (!Boolean.TRUE.equals(result.getCanEmit())) {
            throw new InvalidCounterSaleException("La combinación de counter-sales no es emitible: " + result.getValidationMessages());
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCounterSaleException("Usuario inválido: " + username));

        com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries lockedSeries = saleDocumentSeriesRepository.lockSeries("BOLETA", result.getSeries());
        if (lockedSeries == null || Boolean.FALSE.equals(lockedSeries.getEnabled())) {
            throw new InvalidCounterSaleException("Serie no disponible para BOLETA: " + result.getSeries());
        }
        Long nextNumber = lockedSeries.getNextNumber();

        Long generatedSaleId = saleV2Repository.insertSale(
                result.getAnchor().getStationId(),
                result.getAnchor().getSaleSessionId(),
                user.getId(),
                "BOLETA",
                result.getSeries(),
                nextNumber,
                result.getIssueDate(),
                result.getAnchor().getCurrency(),
                result.getAnchor().getExchangeRate(),
                blankIfNull(result.getAnchor().getPriceList()).isEmpty() ? "A" : result.getAnchor().getPriceList(),
                null,
                "GEN",
                "0",
                "VENTA DIARIA",
                "-",
                result.getAnchor().getTaxStatus(),
                resolveTaxReason(result.getAnchor().getTaxStatus()),
                result.getAnchor().getIgvRate(),
                result.getAnchor().getIgvIncluded(),
                "CONTADO",
                null,
                null,
                result.getNotes()
        );

        int lineNumber = 1;
        for (CounterSaleSunatCombinationComposer.SelectedLine line : result.getSelectedLines()) {
            CounterSaleItemResponse item = line.getItem();
            saleV2Repository.insertSaleItem(
                    generatedSaleId,
                    lineNumber++,
                    item.getProductId(),
                    item.getSku(),
                    item.getDescription(),
                    item.getPresentation(),
                    item.getFactor(),
                    item.getQuantity(),
                    line.getEmittedUnitPrice(),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    "VENDIDO",
                    null,
                    Boolean.TRUE,
                    Boolean.FALSE,
                    Boolean.TRUE,
                    item.getUnitCostSnapshot(),
                    item.getTotalCostSnapshot(),
                    line.getEmittedRevenueTotal()
            );
        }

        saleV2Repository.updateTotals(
                generatedSaleId,
                result.getTotals().getSubtotal(),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                result.getTotals().getIgv(),
                result.getTotals().getTotal(),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
        );
        salePaymentRepository.insert(generatedSaleId, result.getPaymentMethod(), result.getTotals().getTotal());
        saleDocumentSeriesRepository.incrementNextNumber(lockedSeries.getId());

        Long comboId = insertCombo(result, generatedSaleId, user);
        insertMembers(comboId, result);
        insertLines(comboId, result);

        try {
            DocumentRequest externalRequest = buildExternalRequest(result, nextNumber);
            log.info("COUNTER-SALE venta diaria SUNAT request: {}", externalRequest);

            String rawResponse = sunatRestClient.post()
                    .body(externalRequest)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode data = root != null ? root.path("data") : null;

            String code = textValue(data, "respuesta_sunat_codigo");
            String description = defaultIfBlank(textValue(data, "respuesta_sunat_descripcion"), "Respuesta vacía de SUNAT");
            String hashCode = extractHashCode(data != null ? data.path("codigo_hash") : null);
            String xmlPath = textValue(data, "ruta_xml");
            String cdrPath = textValue(data, "ruta_cdr");
            String pdfPath = textValue(data, "ruta_pdf");
            String finalStatus = SUCCESS_RESPONSE.equals(code) ? "ACEPTADO" : "RECHAZADO";
            LocalDateTime emittedAt = LocalDateTime.now();

            updateSaleSunat(generatedSaleId, finalStatus, code, description, hashCode, xmlPath, cdrPath, pdfPath, emittedAt);

            if (!"ACEPTADO".equals(finalStatus)) {
                markComboReleased(comboId, "SUNAT_RECHAZADO: " + description);
                throw new InvalidCounterSaleException("SUNAT no aceptó la emisión de venta diaria.");
            }

            associateCounterSales(result, generatedSaleId, result.getSeries(), nextNumber, emittedAt);
            markComboAccepted(comboId, generatedSaleId, result.getSeries(), nextNumber, emittedAt);

            return CounterSaleSunatCombinationEmitResponse.builder()
                    .comboId(comboId)
                    .generatedSaleId(generatedSaleId)
                    .customerMode(result.getCustomerMode())
                    .docType("BOLETA")
                    .series(result.getSeries())
                    .number(nextNumber)
                    .issueDate(result.getIssueDate())
                    .customer(CounterSaleSunatCombinationCustomerResponse.builder()
                            .name("VENTA DIARIA")
                            .docTypeCode("0")
                            .docNumber("0")
                            .address("-")
                            .build())
                    .currency(result.getAnchor().getCurrency())
                    .taxStatus(result.getAnchor().getTaxStatus())
                    .igvRate(result.getAnchor().getIgvRate())
                    .igvIncluded(result.getAnchor().getIgvIncluded())
                    .composedSubtotal(result.getTotals().getSubtotal())
                    .composedDiscountTotal(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .composedIgvAmount(result.getTotals().getIgv())
                    .composedTotal(result.getTotals().getTotal())
                    .totalLimit(CounterSaleSunatCombinationComposer.TOTAL_LIMIT)
                    .remainingLimit(CounterSaleSunatCombinationComposer.TOTAL_LIMIT.subtract(result.getTotals().getTotal()).setScale(4, RoundingMode.HALF_UP))
                    .withinLimit(result.getWithinLimit())
                    .associatedAt(emittedAt)
                    .emission(CounterSaleSunatEmissionInfoResponse.builder()
                            .saleId(generatedSaleId)
                            .docType("BOLETA")
                            .series(result.getSeries())
                            .number(nextNumber)
                            .sunatStatus(finalStatus)
                            .sunatCode(code)
                            .sunatDescription(description)
                            .hashCode(hashCode)
                            .xmlPath(xmlPath)
                            .cdrPath(cdrPath)
                            .pdfPath(pdfPath)
                            .emittedAt(emittedAt)
                            .build())
                    .linkedCounterSales(result.getSelectedCounterSales().stream().map(row -> CounterSaleSunatCombinationCounterSaleResponse.builder()
                            .counterSaleId(row.getDetail().getCounterSaleId())
                            .sourceDocumentLabel("VENTANILLA " + row.getDetail().getSeries() + "-" + row.getDetail().getNumber())
                            .series(row.getDetail().getSeries())
                            .number(row.getDetail().getNumber())
                            .status(row.getDetail().getStatus())
                            .subtotal(nz(row.getDetail().getSubtotal()).setScale(4, RoundingMode.HALF_UP))
                            .discountTotal(nz(row.getDetail().getDiscountTotal()).setScale(4, RoundingMode.HALF_UP))
                            .igvAmount(nz(row.getDetail().getIgvAmount()).setScale(4, RoundingMode.HALF_UP))
                            .total(nz(row.getDetail().getTotal()).setScale(4, RoundingMode.HALF_UP))
                            .associatedToSunat(Boolean.TRUE)
                            .associatedSaleId(generatedSaleId)
                            .associatedDocType("BOLETA")
                            .associatedSeries(result.getSeries())
                            .associatedNumber(nextNumber)
                            .associatedAt(emittedAt)
                            .build()).toList())
                    .lines(result.getSelectedLines().stream().map(line -> CounterSaleSunatCombinationLineResponse.builder()
                            .counterSaleId(line.getCounterSaleId())
                            .counterSaleItemId(line.getItem().getCounterSaleItemId())
                            .sourceDocumentLabel("VENTANILLA " + line.getCounterSale().getSeries() + "-" + line.getCounterSale().getNumber())
                            .sourceLineNumber(line.getItem().getLineNumber())
                            .productId(line.getItem().getProductId())
                            .sku(line.getItem().getSku())
                            .description(line.getItem().getDescription())
                            .quantity(line.getItem().getQuantity())
                            .originalUnitPrice(nz(line.getItem().getUnitPrice()).setScale(4, RoundingMode.HALF_UP))
                            .emittedUnitPrice(line.getEmittedUnitPrice())
                            .discountPercent(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                            .discountAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                            .originalRevenueTotal(nz(line.getItem().getRevenueTotal()).setScale(4, RoundingMode.HALF_UP))
                            .emittedRevenueTotal(line.getEmittedRevenueTotal())
                            .build()).toList())
                    .build();

        } catch (InvalidCounterSaleException ex) {
            markComboReleased(comboId, ex.getMessage());
            updateSaleSunat(generatedSaleId, "ERROR", null, ex.getMessage(), null, null, null, null, LocalDateTime.now());
            throw ex;
        } catch (Exception ex) {
            markComboReleased(comboId, ex.getMessage());
            updateSaleSunat(generatedSaleId, "ERROR", null, ex.getMessage(), null, null, null, null, LocalDateTime.now());
            throw new InvalidCounterSaleException("No se pudo emitir la venta diaria: " + ex.getMessage());
        }
    }

    private Long insertCombo(CounterSaleSunatCombinationComposer.ComposedResult result, Long saleId, User user) {
        String sql = """
                INSERT INTO counter_sale_sunat_combo(
                  anchor_counter_sale_id, generated_sale_id,
                  doc_type, series, issue_date,
                  currency, tax_status, igv_rate, igv_included,
                  subtotal, discount_total, igv_amount, total,
                  payment_method,
                  customer_mode, customer_id, customer_doc_type, customer_doc_number, customer_name, customer_address,
                  combo_status, notes,
                  reserved_by, reserved_by_username
                ) VALUES (?, ?, 'BOLETA', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                RETURNING id
                """;
        return jdbcClient.sql(sql)
                .params(
                        result.getAnchor().getCounterSaleId(), saleId,
                        result.getSeries(), result.getIssueDate(),
                        result.getAnchor().getCurrency(), result.getAnchor().getTaxStatus(), (result.getAnchor().getIgvRate() == null ? new BigDecimal("18.00") : result.getAnchor().getIgvRate()), Boolean.TRUE.equals(result.getAnchor().getIgvIncluded()),
                        result.getTotals().getSubtotal(), BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), result.getTotals().getIgv(), result.getTotals().getTotal(),
                        result.getPaymentMethod(),
                        result.getCustomerMode(), null, "0", "0", "VENTA DIARIA", "-",
                        result.getNotes(),
                        user.getId(), user.getUsername()
                )
                .query(Long.class)
                .single();
    }

    private void insertMembers(Long comboId, CounterSaleSunatCombinationComposer.ComposedResult result) {
        String sql = """
                INSERT INTO counter_sale_sunat_combo_member(
                  combo_id, position, counter_sale_id, counter_sale_total, counter_sale_discount_total
                ) VALUES (?, ?, ?, ?, ?)
                """;
        int position = 1;
        for (CounterSaleSunatCombinationComposer.SelectedCounterSale row : result.getSelectedCounterSales()) {
            jdbcClient.sql(sql)
                    .params(comboId, position++, row.getDetail().getCounterSaleId(), nz(row.getDetail().getTotal()), nz(row.getDetail().getDiscountTotal()))
                    .update();
        }
    }

    private void insertLines(Long comboId, CounterSaleSunatCombinationComposer.ComposedResult result) {
        String sql = """
                INSERT INTO counter_sale_sunat_combo_line(
                  combo_id, counter_sale_id, counter_sale_item_id, source_line_number,
                  product_id, sku, description, quantity,
                  original_unit_price, emitted_unit_price,
                  original_revenue_total, emitted_revenue_total
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        for (CounterSaleSunatCombinationComposer.SelectedLine line : result.getSelectedLines()) {
            jdbcClient.sql(sql)
                    .params(
                            comboId,
                            line.getCounterSaleId(),
                            line.getItem().getCounterSaleItemId(),
                            line.getItem().getLineNumber(),
                            line.getItem().getProductId(),
                            line.getItem().getSku(),
                            line.getItem().getDescription(),
                            line.getItem().getQuantity(),
                            nz(line.getItem().getUnitPrice()).setScale(4, RoundingMode.HALF_UP),
                            line.getEmittedUnitPrice(),
                            nz(line.getItem().getRevenueTotal()).setScale(4, RoundingMode.HALF_UP),
                            line.getEmittedRevenueTotal()
                    )
                    .update();
        }
    }

    private void associateCounterSales(CounterSaleSunatCombinationComposer.ComposedResult result,
                                       Long saleId,
                                       String series,
                                       Long number,
                                       LocalDateTime associatedAt) {
        String sql = """
                UPDATE counter_sale
                   SET associated_to_sunat = TRUE,
                       associated_sale_id = ?,
                       associated_doc_type = 'BOLETA',
                       associated_series = ?,
                       associated_number = ?,
                       associated_at = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """;
        for (CounterSaleSunatCombinationComposer.SelectedCounterSale row : result.getSelectedCounterSales()) {
            jdbcClient.sql(sql)
                    .params(saleId, series, number, associatedAt, row.getDetail().getCounterSaleId())
                    .update();
        }
    }

    private void markComboAccepted(Long comboId, Long saleId, String series, Long number, LocalDateTime associatedAt) {
        String sql = """
                UPDATE counter_sale_sunat_combo
                   SET combo_status = 'ACEPTADO',
                       generated_sale_id = ?,
                       emitted_doc_type = 'BOLETA',
                       emitted_series = ?,
                       emitted_number = ?,
                       associated_at = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """;
        jdbcClient.sql(sql).params(saleId, series, number, associatedAt, comboId).update();
    }

    private void markComboReleased(Long comboId, String releaseReason) {
        String sql = """
                UPDATE counter_sale_sunat_combo
                   SET combo_status = 'ERROR',
                       error_message = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """;
        jdbcClient.sql(sql).params(truncate(releaseReason, 4000), comboId).update();
    }

    private void updateSaleSunat(Long saleId,
                                 String status,
                                 String responseCode,
                                 String description,
                                 String hashCode,
                                 String xmlPath,
                                 String cdrPath,
                                 String pdfPath,
                                 LocalDateTime sentAt) {
        String sql = """
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
                """;
        jdbcClient.sql(sql)
                .params(status, responseCode, truncate(description, 4000), hashCode, xmlPath, cdrPath, pdfPath, sentAt, saleId)
                .update();
    }

    private DocumentRequest buildExternalRequest(CounterSaleSunatCombinationComposer.ComposedResult result, Long number) {
        SunatProps.Business business = sunatProps.getBusiness();
        LocalDateTime emissionDateTime = LocalDateTime.now();

        return DocumentRequest.builder()
                .business(DocumentRequest.Business.builder()
                        .ruc(business.getRuc())
                        .businessName(business.getBusinessName())
                        .tradeName(business.getTradeName())
                        .taxAddress(business.getTaxAddress())
                        .ubigeo(business.getUbigeo())
                        .neighborhood(business.getNeighborhood())
                        .district(business.getDistrict())
                        .province(business.getProvince())
                        .department(business.getDepartment())
                        .mode(sunatProps.getMode())
                        .username(sunatProps.getUsername())
                        .password(sunatProps.getPassword())
                        .build())
                .customer(DocumentRequest.Customer.builder()
                        .fullName("VENTA DIARIA")
                        .documentNumber("0")
                        .entityTypeCode("0")
                        .address("-")
                        .build())
                .sale(DocumentRequest.Sale.builder()
                        .serial(result.getSeries())
                        .number(String.valueOf(number))
                        .issueDate(result.getIssueDate().format(DATE_FORMATTER))
                        .issueTime(emissionDateTime.toLocalTime().format(TIME_FORMATTER))
                        .dueDate("")
                        .currencyId(mapCurrencyCode(result.getAnchor().getCurrency()))
                        .paymentMethodId("1")
                        .totalTaxed(totalTaxed(result).toPlainString())
                        .totalIgv(totalIgv(result).toPlainString())
                        .totalExempted(totalExempted(result).toPlainString())
                        .totalUnaffected(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .globalDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .documentTypeCode("03")
                        .note(defaultIfBlank(result.getNotes(), "Boleta venta diaria"))
                        .build())
                .items(result.getSelectedLines().stream().map(line -> buildExternalItem(line, result)).toList())
                .build();
    }

    private DocumentRequest.Item buildExternalItem(CounterSaleSunatCombinationComposer.SelectedLine line,
                                                   CounterSaleSunatCombinationComposer.ComposedResult result) {
        BigDecimal qty = nz(line.getItem().getQuantity());
        BigDecimal basePrice = qty.signum() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : line.getEmittedRevenueTotal().divide(qty, 6, RoundingMode.HALF_UP);
        String inferredSunatCode = SunatCodeInferer.infer(line.getItem().getDescription(), line.getItem().getProductCategory());
        return DocumentRequest.Item.builder()
                .product(line.getItem().getDescription())
                .quantity(qty.stripTrailingZeros().toPlainString())
                .basePrice(basePrice.toPlainString())
                .sunatCode(inferredSunatCode)
                .productCode(blankIfNull(line.getItem().getSku()))
                .unitCode("NIU")
                .igvTypeCode("GRAVADA".equalsIgnoreCase(blankIfNull(result.getAnchor().getTaxStatus())) ? "10" : "20")
                .build();
    }

    private BigDecimal totalTaxed(CounterSaleSunatCombinationComposer.ComposedResult result) {
        return "GRAVADA".equalsIgnoreCase(blankIfNull(result.getAnchor().getTaxStatus()))
                ? result.getTotals().getSubtotal().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalIgv(CounterSaleSunatCombinationComposer.ComposedResult result) {
        return "GRAVADA".equalsIgnoreCase(blankIfNull(result.getAnchor().getTaxStatus()))
                ? result.getTotals().getIgv().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalExempted(CounterSaleSunatCombinationComposer.ComposedResult result) {
        return "NO_GRAVADA".equalsIgnoreCase(blankIfNull(result.getAnchor().getTaxStatus()))
                ? result.getTotals().getSubtotal().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String mapCurrencyCode(String currency) {
        String value = blankIfNull(currency).trim().toUpperCase();
        if ("USD".equals(value)) return "2";
        return "1";
    }

    private String resolveTaxReason(String taxStatus) {
        return "NO_GRAVADA".equalsIgnoreCase(blankIfNull(taxStatus)) ? "EXONERADA" : null;
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) return null;
        return child.asText();
    }

    private String extractHashCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    String value = item.asText();
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode codeNode = node.path("codigo");
            if (!codeNode.isMissingNode() && !codeNode.isNull()) return codeNode.asText();
            JsonNode hashNode = node.path("hash");
            if (!hashNode.isMissingNode() && !hashNode.isNull()) return hashNode.asText();
        }
        String value = node.asText();
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
