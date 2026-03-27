package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.InvalidGuideRemissionException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresGuideRemissionRepository implements GuideRemissionRepository {
    private final JdbcClient jdbcClient;

    @Override
    public void saveSubmission(GuideRemissionCompany company, GuideRemissionSubmission request, GuideRemissionSubmissionResponse response) {
        Long existingId = findId(company.getRuc(), request.getGuia().getSerie(), request.getGuia().getNumero()).orElse(null);

        if (existingId == null) {
            existingId = insertHeader(company, request, response);
        } else {
            updateHeader(existingId, company, request, response);
            deleteItemAllocations(existingId);
            deleteItems(existingId);
            deleteRelatedDocuments(existingId);
        }

        insertRelatedDocuments(existingId, request.getRelatedDocuments());
        insertItems(existingId, request.getItems());
    }

    @Override
    public void saveTicketStatus(String companyRuc, GuideRemissionTicketQuery request, GuideRemissionTicketStatusResponse response) {
        Long existingId = findId(companyRuc, request.getSerie(), request.getNumero()).orElse(null);
        GuideRemissionStatus status = resolveStatus(response);

        if (existingId == null) {
            String insertSql = """
                    INSERT INTO guide_remissions(
                        company_ruc,
                        serie,
                        numero,
                        ticket,
                        status,
                        ticket_response_code,
                        cdr_generated,
                        cdr_hash,
                        cdr_message,
                        document_description,
                        ruta_xml,
                        ruta_cdr,
                        error_code,
                        ticket_checked_at,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """;

            jdbcClient.sql(insertSql)
                    .params(
                            companyRuc,
                            request.getSerie(),
                            request.getNumero(),
                            request.getTicket(),
                            status.name(),
                            firstNonBlank(response.getCdrResponseCode(), response.getTicketRpta()),
                            response.getIndCdrGenerado(),
                            response.getCdrHash(),
                            response.getCdrMsjSunat(),
                            response.getDocumentDescription(),
                            response.getRutaXml(),
                            response.getRutaCdr(),
                            response.getNumerror(),
                            OffsetDateTime.now()
                    )
                    .update();
            return;
        }

        String updateSql = """
                UPDATE guide_remissions
                   SET ticket = ?,
                       status = ?,
                       ticket_response_code = ?,
                       cdr_generated = ?,
                       cdr_hash = ?,
                       cdr_message = ?,
                       document_description = ?,
                       ruta_xml = ?,
                       ruta_cdr = ?,
                       error_code = ?,
                       ticket_checked_at = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """;

        jdbcClient.sql(updateSql)
                .params(
                        request.getTicket(),
                        status.name(),
                        firstNonBlank(response.getCdrResponseCode(), response.getTicketRpta()),
                        response.getIndCdrGenerado(),
                        response.getCdrHash(),
                        response.getCdrMsjSunat(),
                        response.getDocumentDescription(),
                        response.getRutaXml(),
                        response.getRutaCdr(),
                        response.getNumerror(),
                        OffsetDateTime.now(),
                        existingId
                )
                .update();
    }

    @Override
    public Optional<GuideRemissionDocument> findDocument(String companyRuc, String serie, String numero) {
        List<GuideRemissionDocument> documents = jdbcClient.sql("""
                SELECT id,
                       company_ruc,
                       serie,
                       numero,
                       issue_date,
                       issue_time,
                       transfer_date,
                       transfer_reason_code,
                       transfer_mode_code,
                       related_document_type_code,
                       related_document_serie,
                       related_document_numero,
                       transporter_document_number,
                       transporter_name,
                       legacy_transport_entity_id,
                       legacy_transport_mtc_number,
                       driver_dni,
                       driver_full_name,
                       driver_license,
                       vehicle_plate,
                       recipient_document_type,
                       recipient_document_number,
                       recipient_name,
                       departure_ubigeo,
                       departure_address,
                       departure_establishment_code,
                       arrival_ubigeo,
                       arrival_address,
                       arrival_establishment_code,
                       total_weight,
                       number_of_packages,
                       notes,
                       ticket,
                       status,
                       ticket_response_code,
                       cdr_generated,
                       cdr_hash,
                       cdr_message,
                       document_description,
                       submitted_at
                  FROM guide_remissions
                 WHERE company_ruc = ?
                   AND serie = ?
                   AND numero = ?
                """)
                .params(companyRuc, serie, numero)
                .query((rs, rowNum) -> GuideRemissionDocument.builder()
                        .id(rs.getLong("id"))
                        .companyRuc(rs.getString("company_ruc"))
                        .serie(rs.getString("serie"))
                        .numero(rs.getString("numero"))
                        .issueDate(rs.getObject("issue_date", LocalDate.class))
                        .issueTime(rs.getObject("issue_time", LocalTime.class))
                        .transferDate(rs.getObject("transfer_date", LocalDate.class))
                        .transferReasonCode(rs.getString("transfer_reason_code"))
                        .transferModeCode(rs.getString("transfer_mode_code"))
                        .relatedDocumentTypeCode(rs.getString("related_document_type_code"))
                        .relatedDocumentSerie(rs.getString("related_document_serie"))
                        .relatedDocumentNumero(rs.getString("related_document_numero"))
                        .transporterDocumentNumber(rs.getString("transporter_document_number"))
                        .transporterName(rs.getString("transporter_name"))
                        .legacyTransportEntityId(rs.getString("legacy_transport_entity_id"))
                        .legacyTransportMtcNumber(rs.getString("legacy_transport_mtc_number"))
                        .driverDni(rs.getString("driver_dni"))
                        .driverFullName(rs.getString("driver_full_name"))
                        .driverLicense(rs.getString("driver_license"))
                        .vehiclePlate(rs.getString("vehicle_plate"))
                        .recipientDocumentType(rs.getObject("recipient_document_type", Integer.class))
                        .recipientDocumentNumber(rs.getString("recipient_document_number"))
                        .recipientName(rs.getString("recipient_name"))
                        .departureUbigeo(rs.getString("departure_ubigeo"))
                        .departureAddress(rs.getString("departure_address"))
                        .departureEstablishmentCode(rs.getString("departure_establishment_code"))
                        .arrivalUbigeo(rs.getString("arrival_ubigeo"))
                        .arrivalAddress(rs.getString("arrival_address"))
                        .arrivalEstablishmentCode(rs.getString("arrival_establishment_code"))
                        .totalWeight(rs.getBigDecimal("total_weight"))
                        .numberOfPackages(rs.getString("number_of_packages"))
                        .notes(rs.getString("notes"))
                        .ticket(rs.getString("ticket"))
                        .status(rs.getString("status"))
                        .ticketResponseCode(rs.getString("ticket_response_code"))
                        .cdrGenerated(rs.getString("cdr_generated"))
                        .cdrHash(rs.getString("cdr_hash"))
                        .cdrMessage(rs.getString("cdr_message"))
                        .documentDescription(rs.getString("document_description"))
                        .submittedAt(rs.getObject("submitted_at", OffsetDateTime.class))
                        .relatedDocuments(new ArrayList<>())
                        .items(new ArrayList<>())
                        .build())
                .list();

        if (documents.isEmpty()) {
            return Optional.empty();
        }

        GuideRemissionDocument document = documents.get(0);
        document.setRelatedDocuments(loadRelatedDocuments(document));
        document.setItems(loadItems(document.getId()));
        return Optional.of(document);
    }

    @Override
    public GuideRemissionGeneratedSeries reserveNextGuideRemissionSeries() {
        List<DocumentSeriesRow> rows = jdbcClient.sql("""
                SELECT id, series, next_number
                  FROM document_series
                 WHERE UPPER(doc_type) = ?
                   AND enabled = TRUE
                 FOR UPDATE
                """)
                .param("GUIDE_REMISSION")
                .query((rs, rowNum) -> new DocumentSeriesRow(
                        rs.getLong("id"),
                        rs.getString("series"),
                        rs.getLong("next_number")
                ))
                .list();

        if (rows.isEmpty()) {
            throw new InvalidGuideRemissionException(
                    "No existe una serie activa en document_series para el doc_type GUIDE_REMISSION."
            );
        }
        if (rows.size() > 1) {
            throw new InvalidGuideRemissionException(
                    "Existe más de una serie activa en document_series para el doc_type GUIDE_REMISSION."
            );
        }

        DocumentSeriesRow row = rows.get(0);
        if (!hasText(row.series())) {
            throw new InvalidGuideRemissionException(
                    "La serie configurada en document_series para GUIDE_REMISSION no es válida."
            );
        }
        if (row.nextNumber() <= 0) {
            throw new InvalidGuideRemissionException(
                    "El next_number configurado en document_series para GUIDE_REMISSION debe ser mayor a cero."
            );
        }

        jdbcClient.sql("""
                UPDATE document_series
                   SET next_number = next_number + 1
                 WHERE id = ?
                """)
                .param(row.id())
                .update();

        return GuideRemissionGeneratedSeries.builder()
                .serie(row.series().trim())
                .numero(String.valueOf(row.nextNumber()))
                .build();
    }

    @Override
    public GuideRemissionPageResult searchPage(String companyRuc, GuideRemissionPageCriteria criteria) {
        List<Object> params = new ArrayList<>();
        String whereClause = buildSearchWhereClause(companyRuc, criteria, params);

        Long total = jdbcClient.sql("SELECT COUNT(*) FROM guide_remissions gr " + whereClause)
                .params(params)
                .query(Long.class)
                .single();

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(criteria.getSize());
        dataParams.add(criteria.getPage() * criteria.getSize());

        List<GuideRemissionPageItem> items = jdbcClient.sql("""
                SELECT gr.id,
                       gr.serie,
                       gr.numero,
                       gr.issue_date,
                       gr.issue_time,
                       gr.transfer_date,
                       gr.status,
                       gr.recipient_document_number,
                       gr.recipient_name,
                       gr.transporter_document_number,
                       gr.transporter_name,
                       gr.total_weight,
                       gr.number_of_packages,
                       gr.ticket,
                       gr.ticket_response_code,
                       gr.related_document_type_code,
                       gr.related_document_serie,
                       gr.related_document_numero,
                       gr.submitted_at,
                       gr.created_at,
                       CASE
                           WHEN COALESCE(rd.related_documents_count, 0) > 0 THEN rd.related_documents_count
                           WHEN gr.related_document_type_code IS NOT NULL
                            AND gr.related_document_serie IS NOT NULL
                            AND gr.related_document_numero IS NOT NULL THEN 1
                           ELSE 0
                       END AS related_documents_count,
                       COALESCE(gi.items_count, 0) AS items_count
                  FROM guide_remissions gr
                  LEFT JOIN LATERAL (
                      SELECT COUNT(*) AS related_documents_count
                        FROM guide_remission_related_documents grd
                       WHERE grd.guide_remission_id = gr.id
                  ) rd ON TRUE
                  LEFT JOIN LATERAL (
                      SELECT COUNT(*) AS items_count
                        FROM guide_remission_items gri
                       WHERE gri.guide_remission_id = gr.id
                  ) gi ON TRUE
                """ + whereClause + """
                 ORDER BY gr.created_at DESC, gr.id DESC
                 LIMIT ? OFFSET ?
                """)
                .params(dataParams)
                .query((rs, rowNum) -> GuideRemissionPageItem.builder()
                        .id(rs.getLong("id"))
                        .serie(rs.getString("serie"))
                        .numero(rs.getString("numero"))
                        .issueDate(rs.getObject("issue_date", LocalDate.class))
                        .issueTime(rs.getObject("issue_time", LocalTime.class))
                        .transferDate(rs.getObject("transfer_date", LocalDate.class))
                        .status(rs.getString("status"))
                        .recipientDocumentNumber(rs.getString("recipient_document_number"))
                        .recipientName(rs.getString("recipient_name"))
                        .transporterDocumentNumber(rs.getString("transporter_document_number"))
                        .transporterName(rs.getString("transporter_name"))
                        .totalWeight(rs.getBigDecimal("total_weight"))
                        .numberOfPackages(rs.getString("number_of_packages"))
                        .ticket(rs.getString("ticket"))
                        .ticketResponseCode(rs.getString("ticket_response_code"))
                        .primaryRelatedDocumentTypeCode(rs.getString("related_document_type_code"))
                        .primaryRelatedDocumentSerie(rs.getString("related_document_serie"))
                        .primaryRelatedDocumentNumero(rs.getString("related_document_numero"))
                        .relatedDocumentsCount(Math.toIntExact(rs.getLong("related_documents_count")))
                        .itemsCount(Math.toIntExact(rs.getLong("items_count")))
                        .submittedAt(rs.getObject("submitted_at", OffsetDateTime.class))
                        .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                        .build())
                .list();

        return GuideRemissionPageResult.builder()
                .items(items)
                .totalElements(total != null ? total : 0L)
                .build();
    }

    private Long insertHeader(GuideRemissionCompany company,
                              GuideRemissionSubmission request,
                              GuideRemissionSubmissionResponse response) {
        GuideRemissionData guia = request.getGuia();
        GuideRemissionRelatedDocument primaryDocument = primaryRelatedDocument(request);

        String insertSql = """
                INSERT INTO guide_remissions(
                    company_ruc,
                    serie,
                    numero,
                    issue_date,
                    issue_time,
                    transfer_date,
                    transfer_reason_code,
                    transfer_mode_code,
                    related_document_type_code,
                    related_document_serie,
                    related_document_numero,
                    transporter_document_number,
                    transporter_name,
                    legacy_transport_entity_id,
                    legacy_transport_mtc_number,
                    driver_dni,
                    driver_full_name,
                    driver_license,
                    vehicle_plate,
                    recipient_document_type,
                    recipient_document_number,
                    recipient_name,
                    departure_ubigeo,
                    departure_address,
                    departure_establishment_code,
                    arrival_ubigeo,
                    arrival_address,
                    arrival_establishment_code,
                    total_weight,
                    number_of_packages,
                    notes,
                    ticket,
                    status,
                    submitted_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcClient.sql(insertSql)
                .params(
                        company.getRuc(),
                        guia.getSerie(),
                        guia.getNumero(),
                        parseLocalDate(guia.getFechaEmision()),
                        parseLocalTime(guia.getHoraEmision()),
                        parseLocalDate(guia.getFechaTraslado()),
                        guia.getGuiaMotivoTraslado(),
                        guia.getGuiaModalidadTraslado(),
                        primaryDocument != null ? normalizeRelatedDocumentType(primaryDocument.getDocumentTypeCode()) : null,
                        primaryDocument != null ? primaryDocument.getSerie() : null,
                        primaryDocument != null ? primaryDocument.getNumero() : null,
                        firstNonBlank(guia.getNumeroDocumentoTransporte(), guia.getEntidadIdTransporte()),
                        guia.getEntidadTransporte(),
                        guia.getEntidadIdTransporte(),
                        guia.getNumeroMtcTransporte(),
                        guia.getConductorDni(),
                        buildDriverFullName(guia),
                        guia.getConductorLicencia(),
                        guia.getVehiculoPlaca(),
                        guia.getDestinatarioTipo(),
                        guia.getDestinatarioNumeroDocumento(),
                        guia.getDestinatarioNombresRazon(),
                        guia.getPartidaUbigeo(),
                        guia.getPartidaDireccion(),
                        guia.getPartidaCodigoEstablecimiento(),
                        guia.getLlegadaUbigeo(),
                        guia.getLlegadaDireccion(),
                        guia.getLlegadaCodigoEstablecimiento(),
                        parseBigDecimal(guia.getPesoTotal()),
                        guia.getNumeroBultos(),
                        guia.getNotas(),
                        response != null ? response.getNumTicket() : null,
                        GuideRemissionStatus.SUBMITTED.name(),
                        parseOffsetDateTime(response != null ? response.getFecRecepcion() : null)
                )
                .update(keyHolder, "id");

        return Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();
    }

    private void updateHeader(Long id,
                              GuideRemissionCompany company,
                              GuideRemissionSubmission request,
                              GuideRemissionSubmissionResponse response) {
        GuideRemissionData guia = request.getGuia();
        GuideRemissionRelatedDocument primaryDocument = primaryRelatedDocument(request);

        String updateSql = """
                UPDATE guide_remissions
                   SET company_ruc = ?,
                       issue_date = ?,
                       issue_time = ?,
                       transfer_date = ?,
                       transfer_reason_code = ?,
                       transfer_mode_code = ?,
                       related_document_type_code = ?,
                       related_document_serie = ?,
                       related_document_numero = ?,
                       transporter_document_number = ?,
                       transporter_name = ?,
                       legacy_transport_entity_id = ?,
                       legacy_transport_mtc_number = ?,
                       driver_dni = ?,
                       driver_full_name = ?,
                       driver_license = ?,
                       vehicle_plate = ?,
                       recipient_document_type = ?,
                       recipient_document_number = ?,
                       recipient_name = ?,
                       departure_ubigeo = ?,
                       departure_address = ?,
                       departure_establishment_code = ?,
                       arrival_ubigeo = ?,
                       arrival_address = ?,
                       arrival_establishment_code = ?,
                       total_weight = ?,
                       number_of_packages = ?,
                       notes = ?,
                       ticket = ?,
                       status = ?,
                       submitted_at = ?,
                       updated_at = NOW()
                 WHERE id = ?
                """;

        jdbcClient.sql(updateSql)
                .params(
                        company.getRuc(),
                        parseLocalDate(guia.getFechaEmision()),
                        parseLocalTime(guia.getHoraEmision()),
                        parseLocalDate(guia.getFechaTraslado()),
                        guia.getGuiaMotivoTraslado(),
                        guia.getGuiaModalidadTraslado(),
                        primaryDocument != null ? normalizeRelatedDocumentType(primaryDocument.getDocumentTypeCode()) : null,
                        primaryDocument != null ? primaryDocument.getSerie() : null,
                        primaryDocument != null ? primaryDocument.getNumero() : null,
                        firstNonBlank(guia.getNumeroDocumentoTransporte(), guia.getEntidadIdTransporte()),
                        guia.getEntidadTransporte(),
                        guia.getEntidadIdTransporte(),
                        guia.getNumeroMtcTransporte(),
                        guia.getConductorDni(),
                        buildDriverFullName(guia),
                        guia.getConductorLicencia(),
                        guia.getVehiculoPlaca(),
                        guia.getDestinatarioTipo(),
                        guia.getDestinatarioNumeroDocumento(),
                        guia.getDestinatarioNombresRazon(),
                        guia.getPartidaUbigeo(),
                        guia.getPartidaDireccion(),
                        guia.getPartidaCodigoEstablecimiento(),
                        guia.getLlegadaUbigeo(),
                        guia.getLlegadaDireccion(),
                        guia.getLlegadaCodigoEstablecimiento(),
                        parseBigDecimal(guia.getPesoTotal()),
                        guia.getNumeroBultos(),
                        guia.getNotas(),
                        response != null ? response.getNumTicket() : null,
                        GuideRemissionStatus.SUBMITTED.name(),
                        parseOffsetDateTime(response != null ? response.getFecRecepcion() : null),
                        id
                )
                .update();
    }

    private void insertRelatedDocuments(Long guideRemissionId, List<GuideRemissionRelatedDocument> relatedDocuments) {
        if (relatedDocuments == null || relatedDocuments.isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO guide_remission_related_documents(
                    guide_remission_id,
                    line_no,
                    document_type_code,
                    document_serie,
                    document_numero,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                """;

        int lineNo = 1;
        for (GuideRemissionRelatedDocument document : relatedDocuments) {
            jdbcClient.sql(insertSql)
                    .params(
                            guideRemissionId,
                            lineNo++,
                            normalizeRelatedDocumentType(document.getDocumentTypeCode()),
                            document.getSerie(),
                            document.getNumero()
                    )
                    .update();
        }
    }

    private void insertItems(Long guideRemissionId, List<GuideRemissionItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        String insertItemSql = """
                INSERT INTO guide_remission_items(
                    guide_remission_id,
                    line_no,
                    quantity,
                    description,
                    item_code,
                    unit_code
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        String insertAllocationSql = """
                INSERT INTO guide_remission_item_allocations(
                    guide_remission_id,
                    guide_item_line_no,
                    allocation_line_no,
                    related_document_type_code,
                    related_document_serie,
                    related_document_numero,
                    related_document_line_no,
                    source_item_code,
                    source_item_description,
                    quantity,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

        int lineNo = 1;
        for (GuideRemissionItem item : items) {
            int currentLineNo = lineNo++;

            jdbcClient.sql(insertItemSql)
                    .params(
                            guideRemissionId,
                            currentLineNo,
                            parseBigDecimal(item.getCantidad()),
                            item.getDescripcion(),
                            item.getCodigo(),
                            item.getCodigoUnidad()
                    )
                    .update();

            if (item.getSourceLines() == null || item.getSourceLines().isEmpty()) {
                continue;
            }

            int allocationLineNo = 1;
            for (GuideRemissionItemSourceLine sourceLine : item.getSourceLines()) {
                jdbcClient.sql(insertAllocationSql)
                        .params(
                                guideRemissionId,
                                currentLineNo,
                                allocationLineNo++,
                                normalizeRelatedDocumentType(sourceLine.getRelatedDocumentTypeCode()),
                                sourceLine.getRelatedDocumentSerie(),
                                sourceLine.getRelatedDocumentNumero(),
                                sourceLine.getRelatedDocumentLineNo(),
                                sourceLine.getSourceItemCode(),
                                sourceLine.getSourceItemDescription(),
                                parseBigDecimal(sourceLine.getCantidad())
                        )
                        .update();
            }
        }
    }

    private void deleteItemAllocations(Long guideRemissionId) {
        jdbcClient.sql("DELETE FROM guide_remission_item_allocations WHERE guide_remission_id = ?")
                .param(guideRemissionId)
                .update();
    }

    private void deleteItems(Long guideRemissionId) {
        jdbcClient.sql("DELETE FROM guide_remission_items WHERE guide_remission_id = ?")
                .param(guideRemissionId)
                .update();
    }

    private void deleteRelatedDocuments(Long guideRemissionId) {
        jdbcClient.sql("DELETE FROM guide_remission_related_documents WHERE guide_remission_id = ?")
                .param(guideRemissionId)
                .update();
    }

    private Optional<Long> findId(String companyRuc, String serie, String numero) {
        return jdbcClient.sql("""
                SELECT id
                  FROM guide_remissions
                 WHERE company_ruc = ?
                   AND serie = ?
                   AND numero = ?
                """)
                .params(companyRuc, serie, numero)
                .query(Long.class)
                .optional();
    }

    private List<GuideRemissionRelatedDocument> loadRelatedDocuments(GuideRemissionDocument document) {
        List<GuideRemissionRelatedDocument> relatedDocuments = jdbcClient.sql("""
                SELECT line_no,
                       document_type_code,
                       document_serie,
                       document_numero
                  FROM guide_remission_related_documents
                 WHERE guide_remission_id = ?
                 ORDER BY line_no
                """)
                .param(document.getId())
                .query((rs, rowNum) -> GuideRemissionRelatedDocument.builder()
                        .documentTypeCode(rs.getString("document_type_code"))
                        .serie(rs.getString("document_serie"))
                        .numero(rs.getString("document_numero"))
                        .build())
                .list();

        if (!relatedDocuments.isEmpty()) {
            return relatedDocuments;
        }

        if (hasText(document.getRelatedDocumentTypeCode())
                && hasText(document.getRelatedDocumentSerie())
                && hasText(document.getRelatedDocumentNumero())) {
            return List.of(GuideRemissionRelatedDocument.builder()
                    .documentTypeCode(document.getRelatedDocumentTypeCode())
                    .serie(document.getRelatedDocumentSerie())
                    .numero(document.getRelatedDocumentNumero())
                    .build());
        }

        return new ArrayList<>();
    }

    private List<GuideRemissionDocumentItem> loadItems(Long guideRemissionId) {
        List<GuideRemissionDocumentItem> items = jdbcClient.sql("""
                SELECT line_no, quantity, description, item_code, unit_code
                  FROM guide_remission_items
                 WHERE guide_remission_id = ?
                 ORDER BY line_no
                """)
                .param(guideRemissionId)
                .query((rs, rowNum) -> GuideRemissionDocumentItem.builder()
                        .lineNo(rs.getObject("line_no", Integer.class))
                        .quantity(rs.getBigDecimal("quantity"))
                        .description(rs.getString("description"))
                        .itemCode(rs.getString("item_code"))
                        .unitCode(rs.getString("unit_code"))
                        .sourceAllocations(new ArrayList<>())
                        .build())
                .list();

        Map<Integer, List<GuideRemissionDocumentItemAllocation>> allocationsByItemLine = new LinkedHashMap<>();
        List<AllocationRow> allocationRows = jdbcClient.sql("""
                SELECT guide_item_line_no,
                       allocation_line_no,
                       related_document_type_code,
                       related_document_serie,
                       related_document_numero,
                       related_document_line_no,
                       source_item_code,
                       source_item_description,
                       quantity
                  FROM guide_remission_item_allocations
                 WHERE guide_remission_id = ?
                 ORDER BY guide_item_line_no, allocation_line_no
                """)
                .param(guideRemissionId)
                .query((rs, rowNum) -> new AllocationRow(
                        rs.getObject("guide_item_line_no", Integer.class),
                        GuideRemissionDocumentItemAllocation.builder()
                                .allocationLineNo(rs.getObject("allocation_line_no", Integer.class))
                                .relatedDocumentTypeCode(rs.getString("related_document_type_code"))
                                .relatedDocumentSerie(rs.getString("related_document_serie"))
                                .relatedDocumentNumero(rs.getString("related_document_numero"))
                                .relatedDocumentLineNo(rs.getObject("related_document_line_no", Integer.class))
                                .sourceItemCode(rs.getString("source_item_code"))
                                .sourceItemDescription(rs.getString("source_item_description"))
                                .quantity(rs.getBigDecimal("quantity"))
                                .build()
                ))
                .list();

        for (AllocationRow row : allocationRows) {
            allocationsByItemLine.computeIfAbsent(row.guideItemLineNo(), key -> new ArrayList<>()).add(row.allocation());
        }

        for (GuideRemissionDocumentItem item : items) {
            item.setSourceAllocations(allocationsByItemLine.getOrDefault(item.getLineNo(), new ArrayList<>()));
        }

        return items;
    }

    private String buildSearchWhereClause(String companyRuc, GuideRemissionPageCriteria criteria, List<Object> params) {
        StringBuilder sql = new StringBuilder(" WHERE gr.company_ruc = ?");
        params.add(companyRuc);

        if (hasText(criteria.getQuery())) {
            String like = like(criteria.getQuery());
            sql.append("""
                     AND (
                            UPPER(gr.serie) LIKE ?
                         OR UPPER(gr.numero) LIKE ?
                         OR UPPER(COALESCE(gr.recipient_name, '')) LIKE ?
                         OR UPPER(COALESCE(gr.recipient_document_number, '')) LIKE ?
                         OR UPPER(COALESCE(gr.ticket, '')) LIKE ?
                         OR UPPER(COALESCE(gr.transporter_name, '')) LIKE ?
                         OR UPPER(COALESCE(gr.related_document_type_code, '') || ' ' || COALESCE(gr.related_document_serie, '') || '-' || COALESCE(gr.related_document_numero, '')) LIKE ?
                         OR EXISTS (
                                SELECT 1
                                  FROM guide_remission_related_documents grd
                                 WHERE grd.guide_remission_id = gr.id
                                   AND UPPER(COALESCE(grd.document_type_code, '') || ' ' || COALESCE(grd.document_serie, '') || '-' || COALESCE(grd.document_numero, '')) LIKE ?
                         )
                     )
                    """);
            for (int i = 0; i < 8; i++) {
                params.add(like);
            }
        }

        if (hasText(criteria.getStatus())) {
            sql.append(" AND UPPER(gr.status) = ?");
            params.add(criteria.getStatus().trim().toUpperCase());
        }
        if (hasText(criteria.getSerie())) {
            sql.append(" AND UPPER(gr.serie) LIKE ?");
            params.add(like(criteria.getSerie()));
        }
        if (hasText(criteria.getNumero())) {
            sql.append(" AND UPPER(gr.numero) LIKE ?");
            params.add(like(criteria.getNumero()));
        }
        if (hasText(criteria.getRecipientDocumentNumber())) {
            sql.append(" AND UPPER(COALESCE(gr.recipient_document_number, '')) LIKE ?");
            params.add(like(criteria.getRecipientDocumentNumber()));
        }
        if (hasText(criteria.getRelatedDocument())) {
            sql.append("""
                     AND (
                            UPPER(COALESCE(gr.related_document_type_code, '') || ' ' || COALESCE(gr.related_document_serie, '') || '-' || COALESCE(gr.related_document_numero, '')) LIKE ?
                         OR EXISTS (
                                SELECT 1
                                  FROM guide_remission_related_documents grd
                                 WHERE grd.guide_remission_id = gr.id
                                   AND UPPER(COALESCE(grd.document_type_code, '') || ' ' || COALESCE(grd.document_serie, '') || '-' || COALESCE(grd.document_numero, '')) LIKE ?
                         )
                     )
                    """);
            String relatedDocumentLike = like(criteria.getRelatedDocument());
            params.add(relatedDocumentLike);
            params.add(relatedDocumentLike);
        }
        if (criteria.getIssueDateFrom() != null) {
            sql.append(" AND gr.issue_date >= ?");
            params.add(criteria.getIssueDateFrom());
        }
        if (criteria.getIssueDateTo() != null) {
            sql.append(" AND gr.issue_date <= ?");
            params.add(criteria.getIssueDateTo());
        }
        if (criteria.getTransferDateFrom() != null) {
            sql.append(" AND gr.transfer_date >= ?");
            params.add(criteria.getTransferDateFrom());
        }
        if (criteria.getTransferDateTo() != null) {
            sql.append(" AND gr.transfer_date <= ?");
            params.add(criteria.getTransferDateTo());
        }

        return sql.toString();
    }

    private String like(String value) {
        return "%" + value.trim().toUpperCase() + "%";
    }

    private GuideRemissionRelatedDocument primaryRelatedDocument(GuideRemissionSubmission request) {
        if (request.getRelatedDocuments() != null && !request.getRelatedDocuments().isEmpty()) {
            return request.getRelatedDocuments().get(0);
        }
        if (hasText(request.getRelatedDocumentTypeCode())
                && hasText(request.getRelatedDocumentSerie())
                && hasText(request.getRelatedDocumentNumero())) {
            return GuideRemissionRelatedDocument.builder()
                    .documentTypeCode(request.getRelatedDocumentTypeCode())
                    .serie(request.getRelatedDocumentSerie())
                    .numero(request.getRelatedDocumentNumero())
                    .build();
        }
        return null;
    }

    private GuideRemissionStatus resolveStatus(GuideRemissionTicketStatusResponse response) {
        String responseCode = firstNonBlank(response.getCdrResponseCode(), response.getTicketRpta());

        if ("0".equals(responseCode)) {
            return GuideRemissionStatus.ACCEPTED;
        }
        if ("98".equals(responseCode)) {
            return GuideRemissionStatus.PROCESSING;
        }
        if ("99".equals(responseCode)) {
            return GuideRemissionStatus.REJECTED;
        }
        return GuideRemissionStatus.TICKET_CHECKED;
    }

    private LocalDate parseLocalDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private LocalTime parseLocalTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private String normalizeRelatedDocumentType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "01", "FACTURA" -> "01";
            case "03", "BOLETA", "BOLETA ELECTRONICA", "BOLETA VENTA ELECTRONICA" -> "03";
            default -> value.trim();
        };
    }

    private String buildDriverFullName(GuideRemissionData guia) {
        String names = (guia.getConductorNombres() == null ? "" : guia.getConductorNombres().trim());
        String lastNames = (guia.getConductorApellidos() == null ? "" : guia.getConductorApellidos().trim());
        String fullName = (names + " " + lastNames).trim();
        return fullName.isBlank() ? null : fullName;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record AllocationRow(Integer guideItemLineNo, GuideRemissionDocumentItemAllocation allocation) {
    }

    private record DocumentSeriesRow(Long id, String series, long nextNumber) {
    }
}
