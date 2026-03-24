package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import org.springframework.stereotype.Component;

@Component
public class GuideRemissionPhpResponseEvaluator {

    public boolean isTokenInvalid(GuideRemissionSubmissionResponse response) {
        return isTokenInvalid(response != null ? response.getCod() : null, response != null ? response.getMsg() : null);
    }

    public boolean isTokenInvalid(GuideRemissionTicketStatusResponse response) {
        return isTokenInvalid(response != null ? response.getCod() : null, response != null ? response.getMsg() : null);
    }

    public void assertValidTokenResponse(GuideRemissionTokenResponse response) {
        if (response == null) {
            throw new GuideRemissionIntegrationException("El servicio de token respondió vacío.");
        }
        if (response.getCod() != null) {
            throw new GuideRemissionIntegrationException(buildPhpErrorMessage(
                    "El servicio de token devolvió un error funcional", response.getCod(), response.getMsg(), response.getExc()));
        }
        if (!hasText(response.getAccessToken())) {
            throw new GuideRemissionIntegrationException("El servicio de token no devolvió access_token.");
        }
    }

    public void assertSuccessfulSubmission(GuideRemissionSubmissionResponse response) {
        if (response == null) {
            throw new GuideRemissionIntegrationException("El envío de la guía respondió vacío.");
        }
        if (response.getCod() != null) {
            throw new GuideRemissionIntegrationException(buildPhpErrorMessage(
                    "El servicio de envío devolvió un error funcional", response.getCod(), response.getMsg(), response.getExc()));
        }
        if (!hasText(response.getNumTicket())) {
            throw new GuideRemissionIntegrationException("El servicio de envío no devolvió numTicket.");
        }
    }

    public void assertSuccessfulTicketQuery(GuideRemissionTicketStatusResponse response) {
        if (response == null) {
            throw new GuideRemissionIntegrationException("La consulta del ticket respondió vacía.");
        }
        if (response.getCod() != null) {
            throw new GuideRemissionIntegrationException(buildPhpErrorMessage(
                    "El servicio de ticket devolvió un error funcional", response.getCod(), response.getMsg(), response.getExc()));
        }
    }

    private boolean isTokenInvalid(Integer cod, String msg) {
        return cod != null
                && cod == 404
                && hasText(msg)
                && msg.toLowerCase().contains("token no existe");
    }

    private String buildPhpErrorMessage(String prefix, Integer cod, String msg, String exc) {
        return prefix + ". cod=" + cod + ", msg=" + defaultString(msg) + ", exc=" + defaultString(exc);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
