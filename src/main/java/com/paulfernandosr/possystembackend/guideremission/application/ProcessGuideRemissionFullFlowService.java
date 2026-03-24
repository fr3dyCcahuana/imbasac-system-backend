package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.ProcessGuideRemissionFullFlowUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessGuideRemissionFullFlowService implements ProcessGuideRemissionFullFlowUseCase {
    private final GuideRemissionBusinessValidator validator;
    private final GuideRemissionProvider guideRemissionProvider;
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionTokenManager tokenManager;
    private final GuideRemissionPhpResponseEvaluator responseEvaluator;

    @Override
    @Transactional
    public GuideRemissionFullFlowResponse process(GuideRemissionFullFlowRequest request) {
        validator.validate(request);
        log.info("[guide-remission][full-flow] Inicio de flujo completo. ruc={}, serie={}, numero={}",
                properties.getCompany().getRuc(), request.getGuia().getSerie(), request.getGuia().getNumero());

        GuideRemissionTokenResolution tokenResolution = tokenManager.getOrCreateToken();
        String initialTokenSource = tokenResolution.getTokenSource();
        boolean retriedWithFreshToken = false;

        GuideRemissionSubmission submissionRequest = toSubmissionRequest(request, tokenResolution.accessToken());
        GuideRemissionSubmissionResponse submissionResponse = guideRemissionProvider.submit(submissionRequest);

        if (responseEvaluator.isTokenInvalid(submissionResponse)) {
            log.warn("[guide-remission][full-flow] Token inválido detectado en envío de guía. serie={}, numero={}",
                    request.getGuia().getSerie(), request.getGuia().getNumero());
            tokenManager.invalidateCachedToken();
            tokenResolution = tokenManager.forceRefreshToken();
            retriedWithFreshToken = true;
            submissionRequest.setToken(tokenResolution.accessToken());
            submissionResponse = guideRemissionProvider.submit(submissionRequest);
        }

        responseEvaluator.assertSuccessfulSubmission(submissionResponse);
        guideRemissionRepository.saveSubmission(properties.toCompanyPayload(), submissionRequest, submissionResponse);
        log.info("[guide-remission][full-flow] Guía enviada. serie={}, numero={}, ticket={}",
                request.getGuia().getSerie(), request.getGuia().getNumero(), maskTicket(submissionResponse.getNumTicket()));

        sleepBeforeTicketQuery();

        GuideRemissionTicketQuery ticketQuery = GuideRemissionTicketQuery.builder()
                .ticket(submissionResponse.getNumTicket())
                .tokenAccess(tokenResolution.accessToken())
                .serie(request.getGuia().getSerie())
                .numero(request.getGuia().getNumero())
                .build();

        GuideRemissionTicketStatusResponse ticketResponse = guideRemissionProvider.queryTicket(ticketQuery);
        if (responseEvaluator.isTokenInvalid(ticketResponse)) {
            log.warn("[guide-remission][full-flow] Token inválido detectado en consulta de ticket. serie={}, numero={}, ticket={}",
                    request.getGuia().getSerie(), request.getGuia().getNumero(), maskTicket(ticketQuery.getTicket()));
            tokenManager.invalidateCachedToken();
            tokenResolution = tokenManager.forceRefreshToken();
            retriedWithFreshToken = true;
            ticketQuery.setTokenAccess(tokenResolution.accessToken());
            ticketResponse = guideRemissionProvider.queryTicket(ticketQuery);
        }

        responseEvaluator.assertSuccessfulTicketQuery(ticketResponse);
        guideRemissionRepository.saveTicketStatus(properties.getCompany().getRuc(), ticketQuery, ticketResponse);
        log.info("[guide-remission][full-flow] Ticket consultado. serie={}, numero={}, responseCode={}, mensaje={}",
                request.getGuia().getSerie(), request.getGuia().getNumero(),
                ticketResponse.getCdrResponseCode(), ticketResponse.getCdrMsjSunat());

        return GuideRemissionFullFlowResponse.builder()
                .initialTokenSource(initialTokenSource)
                .retriedWithFreshToken(retriedWithFreshToken)
                .submission(submissionResponse)
                .ticketStatus(ticketResponse)
                .build();
    }

    private GuideRemissionSubmission toSubmissionRequest(GuideRemissionFullFlowRequest request, String token) {
        return GuideRemissionSubmission.builder()
                .guia(request.getGuia())
                .items(request.getItems())
                .token(token)
                .relatedDocumentTypeCode(request.getRelatedDocumentTypeCode())
                .relatedDocumentSerie(request.getRelatedDocumentSerie())
                .relatedDocumentNumero(request.getRelatedDocumentNumero())
                .build();
    }

    private void sleepBeforeTicketQuery() {
        long delayMillis = properties.getTicketQueryDelayMillis();
        if (delayMillis <= 0) {
            return;
        }
        log.info("[guide-remission][full-flow] Esperando {} ms antes de consultar ticket.", delayMillis);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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
}
