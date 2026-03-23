package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output.http;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GuideRemissionHttpProvider implements GuideRemissionProvider {
    private final RestClient guideRemissionRestClient;

    @Override
    public GuideRemissionTokenResponse requestToken(GuideRemissionTokenRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("guias_client_id", request.getGuiasClientId());
        form.add("guias_client_secret", request.getGuiasClientSecret());
        form.add("ruc", request.getRuc());
        form.add("usu_secundario_produccion_user", request.getUsuSecundarioProduccionUser());
        form.add("usu_secundario_produccion_password", request.getUsuSecundarioProduccionPassword());

        try {
            return guideRemissionRestClient.post()
                    .uri("/1_solicito_token.php")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(GuideRemissionTokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw new GuideRemissionIntegrationException(buildErrorMessage("No se pudo solicitar token de guía de remisión", ex));
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException("No se pudo solicitar token de guía de remisión", ex);
        }
    }

    @Override
    public GuideRemissionSubmissionResponse submit(GuideRemissionSubmission request) {
        try {
            return guideRemissionRestClient.post()
                    .uri("/2_envio_xml_recibe_ticket.php")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GuideRemissionSubmissionResponse.class);
        } catch (RestClientResponseException ex) {
            throw new GuideRemissionIntegrationException(buildErrorMessage("No se pudo enviar la guía de remisión", ex));
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException("No se pudo enviar la guía de remisión", ex);
        }
    }

    @Override
    public GuideRemissionTicketStatusResponse queryTicket(GuideRemissionTicketQuery request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ticket", request.getTicket());
        form.add("token_access", request.getTokenAccess());
        form.add("ruc", request.getRuc());
        form.add("serie", request.getSerie());
        form.add("numero", request.getNumero());

        try {
            return guideRemissionRestClient.post()
                    .uri("/3_envio_ticket.php")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(GuideRemissionTicketStatusResponse.class);
        } catch (RestClientResponseException ex) {
            throw new GuideRemissionIntegrationException(buildErrorMessage("No se pudo consultar el ticket de la guía", ex));
        } catch (Exception ex) {
            throw new GuideRemissionIntegrationException("No se pudo consultar el ticket de la guía", ex);
        }
    }

    private String buildErrorMessage(String baseMessage, RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        return baseMessage + ". Status=" + ex.getStatusCode() + ", body=" + responseBody;
    }
}
