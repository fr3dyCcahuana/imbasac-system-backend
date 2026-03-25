package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
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
import java.util.List;
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
            deleteItems(existingId);
        }

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
                        .items(new ArrayList<>())
                        .build())
                .list();

        if (documents.isEmpty()) {
            return Optional.empty();
        }

        GuideRemissionDocument document = documents.get(0);
        List<GuideRemissionDocumentItem> items = jdbcClient.sql("""
                SELECT line_no, quantity, description, item_code, unit_code
                  FROM guide_remission_items
                 WHERE guide_remission_id = ?
                 ORDER BY line_no
                """)
                .param(document.getId())
                .query((rs, rowNum) -> GuideRemissionDocumentItem.builder()
                        .lineNo(rs.getObject("line_no", Integer.class))
                        .quantity(rs.getBigDecimal("quantity"))
                        .description(rs.getString("description"))
                        .itemCode(rs.getString("item_code"))
                        .unitCode(rs.getString("unit_code"))
                        .build())
                .list();

        document.setItems(items);
        return Optional.of(document);
    }

    private Long insertHeader(GuideRemissionCompany company,
                              GuideRemissionSubmission request,
                              GuideRemissionSubmissionResponse response) {
        GuideRemissionData guia = request.getGuia();

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
                        normalizeRelatedDocumentType(request.getRelatedDocumentTypeCode()),
                        request.getRelatedDocumentSerie(),
                        request.getRelatedDocumentNumero(),
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
                        normalizeRelatedDocumentType(request.getRelatedDocumentTypeCode()),
                        request.getRelatedDocumentSerie(),
                        request.getRelatedDocumentNumero(),
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

    private void insertItems(Long guideRemissionId, List<GuideRemissionItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO guide_remission_items(
                    guide_remission_id,
                    line_no,
                    quantity,
                    description,
                    item_code,
                    unit_code
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        int lineNo = 1;
        for (GuideRemissionItem item : items) {
            jdbcClient.sql(insertSql)
                    .params(
                            guideRemissionId,
                            lineNo++,
                            parseBigDecimal(item.getCantidad()),
                            item.getDescripcion(),
                            item.getCodigo(),
                            item.getCodigoUnidad()
                    )
                    .update();
        }
    }

    private void deleteItems(Long guideRemissionId) {
        jdbcClient.sql("DELETE FROM guide_remission_items WHERE guide_remission_id = ?")
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
}
