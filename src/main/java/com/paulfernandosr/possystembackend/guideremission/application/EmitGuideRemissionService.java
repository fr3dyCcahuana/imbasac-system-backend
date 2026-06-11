package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.InvalidGuideRemissionException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.EmitGuideRemissionUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmitGuideRemissionService implements EmitGuideRemissionUseCase {
    private final GuideRemissionBusinessValidator validator;
    private final GuideRemissionProvider guideRemissionProvider;
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionTokenManager tokenManager;
    private final GuideRemissionPhpResponseEvaluator responseEvaluator;

    @Override
    @Transactional
    public GuideRemissionEmissionResponse emit(String serie, String numero) {
        GuideRemissionDocument document = guideRemissionRepository.findDocument(properties.getCompany().getRuc(), serie, numero)
                .orElseThrow(() -> new InvalidGuideRemissionException("No se encontró la guía de remisión " + serie + "-" + numero + "."));

        if (isAcceptedStatus(document.getStatus())) {
            return GuideRemissionEmissionResponse.builder()
                    .success(true)
                    .status(GuideRemissionStatus.ACCEPTED.name())
                    .message("La guia ya fue aceptada por SUNAT.")
                    .serie(serie)
                    .numero(numero)
                    .build();
        }

        if (isRejectedStatus(document.getStatus())) {
            return GuideRemissionEmissionResponse.builder()
                    .success(false)
                    .status(document.getStatus())
                    .message("La guia fue rechazada por SUNAT y no puede reenviarse con la misma serie y numero.")
                    .serie(serie)
                    .numero(numero)
                    .build();
        }

        if (isPendingSunatStatus(document.getStatus()) && !hasText(document.getTicket())) {
            return GuideRemissionEmissionResponse.builder()
                    .success(false)
                    .status(document.getStatus())
                    .message("La guia figura enviada a SUNAT, pero no tiene ticket registrado para reconsultar.")
                    .serie(serie)
                    .numero(numero)
                    .build();
        }

        GuideRemissionTokenResolution tokenResolution = tokenManager.getOrCreateToken();
        boolean retriedWithFreshToken = false;

        try {
            GuideRemissionSubmissionResponse submissionResponse = null;
            String ticket = document.getTicket();

            if (!hasText(ticket)) {
                GuideRemissionSubmission submissionRequest = toSubmission(document, tokenResolution.accessToken());
                validator.validate(submissionRequest);

                submissionResponse = guideRemissionProvider.submit(submissionRequest);
                if (responseEvaluator.isTokenInvalid(submissionResponse)) {
                    tokenManager.invalidateCachedToken();
                    tokenResolution = tokenManager.forceRefreshToken();
                    retriedWithFreshToken = true;
                    submissionRequest.setToken(tokenResolution.accessToken());
                    submissionResponse = guideRemissionProvider.submit(submissionRequest);
                }

                responseEvaluator.assertSuccessfulSubmission(submissionResponse);
                guideRemissionRepository.saveSubmission(properties.toCompanyPayload(), submissionRequest, submissionResponse);
                ticket = submissionResponse.getNumTicket();
            } else {
                log.info("[guide-remission][emit] Reconsultando ticket existente. serie={}, numero={}, status={}, ticket={}",
                        serie, numero, document.getStatus(), maskTicket(ticket));
            }

            GuideRemissionTicketQuery ticketQuery = GuideRemissionTicketQuery.builder()
                    .ticket(ticket)
                    .tokenAccess(tokenResolution.accessToken())
                    .serie(serie)
                    .numero(numero)
                    .build();

            GuideRemissionTicketStatusResponse ticketResponse = guideRemissionProvider.queryTicket(ticketQuery);
            if (responseEvaluator.isTokenInvalid(ticketResponse)) {
                tokenManager.invalidateCachedToken();
                tokenResolution = tokenManager.forceRefreshToken();
                retriedWithFreshToken = true;
                ticketQuery.setTokenAccess(tokenResolution.accessToken());
                ticketResponse = guideRemissionProvider.queryTicket(ticketQuery);
            }

            responseEvaluator.assertSuccessfulTicketQuery(ticketResponse);
            guideRemissionRepository.saveTicketStatus(properties.getCompany().getRuc(), ticketQuery, ticketResponse);

            String status = resolveStatus(ticketResponse).name();
            return GuideRemissionEmissionResponse.builder()
                    .success(GuideRemissionStatus.ACCEPTED.name().equals(status))
                    .status(status)
                    .message(ticketResponse.getCdrMsjSunat())
                    .serie(serie)
                    .numero(numero)
                    .retriedWithFreshToken(retriedWithFreshToken)
                    .submission(submissionResponse)
                    .ticketStatus(ticketResponse)
                    .build();
        } catch (Exception ex) {
            GuideRemissionStatus errorStatus = classifyError(ex);
            String message = ex.getMessage() != null ? ex.getMessage() : "No se pudo emitir la guía de remisión.";
            guideRemissionRepository.markEmissionError(properties.getCompany().getRuc(), serie, numero, errorStatus, message);
            log.warn("[guide-remission][emit] Emisión fallida. serie={}, numero={}, status={}, message={}",
                    serie, numero, errorStatus, message);
            return GuideRemissionEmissionResponse.builder()
                    .success(false)
                    .status(errorStatus.name())
                    .message(message)
                    .serie(serie)
                    .numero(numero)
                    .retriedWithFreshToken(retriedWithFreshToken)
                    .build();
        }
    }

    private GuideRemissionSubmission toSubmission(GuideRemissionDocument document, String token) {
        return GuideRemissionSubmission.builder()
                .guia(GuideRemissionData.builder()
                        .serie(document.getSerie())
                        .numero(document.getNumero())
                        .fechaEmision(format(document.getIssueDate()))
                        .horaEmision(document.getIssueTime() != null ? document.getIssueTime().format(DateTimeFormatter.ISO_LOCAL_TIME) : null)
                        .fechaTraslado(format(document.getTransferDate()))
                        .fechaEntregaTransportista(format(document.getCarrierDeliveryDate()))
                        .guiaMotivoTraslado(document.getTransferReasonCode())
                        .guiaModalidadTraslado(document.getTransferModeCode())
                        .entidadIdTransporte(document.getLegacyTransportEntityId())
                        .numeroMtcTransporte(document.getLegacyTransportMtcNumber())
                        .numeroDocumentoTransporte(document.getTransporterDocumentNumber())
                        .entidadTransporte(document.getTransporterName())
                        .conductorDni(document.getDriverDni())
                        .conductorNombres(document.getDriverFullName())
                        .conductorApellidos("-")
                        .conductorLicencia(document.getDriverLicense())
                        .vehiculoPlaca(document.getVehiclePlate())
                        .destinatarioTipo(document.getRecipientDocumentType())
                        .destinatarioNumeroDocumento(document.getRecipientDocumentNumber())
                        .destinatarioNombresRazon(document.getRecipientName())
                        .partidaUbigeo(document.getDepartureUbigeo())
                        .partidaDireccion(document.getDepartureAddress())
                        .partidaCodigoEstablecimiento(document.getDepartureEstablishmentCode())
                        .llegadaUbigeo(document.getArrivalUbigeo())
                        .llegadaDireccion(document.getArrivalAddress())
                        .llegadaCodigoEstablecimiento(document.getArrivalEstablishmentCode())
                        .pesoTotal(toPlainString(document.getTotalWeight()))
                        .numeroBultos(document.getNumberOfPackages())
                        .notas(document.getNotes())
                        .build())
                .items(toItems(document.getItems()))
                .token(token)
                .relatedDocumentTypeCode(document.getRelatedDocumentTypeCode())
                .relatedDocumentSerie(document.getRelatedDocumentSerie())
                .relatedDocumentNumero(document.getRelatedDocumentNumero())
                .relatedDocuments(document.getRelatedDocuments())
                .build();
    }

    private List<GuideRemissionItem> toItems(List<GuideRemissionDocumentItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> GuideRemissionItem.builder()
                        .cantidad(toPlainString(item.getQuantity()))
                        .descripcion(item.getDescription())
                        .codigo(item.getItemCode())
                        .codigoUnidad(item.getUnitCode())
                        .sourceLines(toSourceLines(item.getSourceAllocations()))
                        .build())
                .toList();
    }

    private List<GuideRemissionItemSourceLine> toSourceLines(List<GuideRemissionDocumentItemAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return null;
        }
        return allocations.stream()
                .map(allocation -> GuideRemissionItemSourceLine.builder()
                        .relatedDocumentTypeCode(allocation.getRelatedDocumentTypeCode())
                        .relatedDocumentSerie(allocation.getRelatedDocumentSerie())
                        .relatedDocumentNumero(allocation.getRelatedDocumentNumero())
                        .relatedDocumentLineNo(allocation.getRelatedDocumentLineNo())
                        .cantidad(toPlainString(allocation.getQuantity()))
                        .sourceItemCode(allocation.getSourceItemCode())
                        .sourceItemDescription(allocation.getSourceItemDescription())
                        .build())
                .toList();
    }

    private GuideRemissionStatus classifyError(Exception ex) {
        String message = ex.getMessage();
        if (message != null && message.toLowerCase().contains("error funcional")) {
            return GuideRemissionStatus.ERROR;
        }
        return GuideRemissionStatus.ERROR_COMUNICACION;
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

    private boolean isAcceptedStatus(String status) {
        if (!hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.equals(GuideRemissionStatus.ACCEPTED.name())
                || normalized.equals("ACEPTADA")
                || normalized.equals("SUCCESS");
    }

    private boolean isRejectedStatus(String status) {
        if (!hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.equals(GuideRemissionStatus.REJECTED.name())
                || normalized.equals("RECHAZADA");
    }

    private boolean isPendingSunatStatus(String status) {
        if (!hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.equals(GuideRemissionStatus.SUBMITTED.name())
                || normalized.equals(GuideRemissionStatus.PROCESSING.name())
                || normalized.equals(GuideRemissionStatus.TICKET_CHECKED.name())
                || normalized.equals("ENVIADO")
                || normalized.equals("ENVIADA")
                || normalized.equals("EN_PROCESO");
    }

    private String format(java.time.LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ISO_LOCAL_DATE) : null;
    }

    private String toPlainString(BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : null;
    }

    private String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return hasText(second) ? second : null;
    }

    private String maskTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return "";
        }
        if (ticket.length() <= 8) {
            return "***";
        }
        return ticket.substring(0, 4) + "***" + ticket.substring(ticket.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
