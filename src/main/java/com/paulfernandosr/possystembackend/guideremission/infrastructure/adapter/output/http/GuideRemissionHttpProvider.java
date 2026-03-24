package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output.http;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmission;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketQuery;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GuideRemissionHttpProvider implements GuideRemissionProvider {
    private final RestClient guideRemissionRestClient;
    private final GuideRemissionProperties properties;

    @Override
    public GuideRemissionTokenResponse requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("guias_client_id", properties.getAuth().getGuiasClientId());
        form.add("guias_client_secret", properties.getAuth().getGuiasClientSecret());
        form.add("ruc", properties.getCompany().getRuc());
        form.add("usu_secundario_produccion_user", properties.getAuth().getUsuSecundarioProduccionUser());
        form.add("usu_secundario_produccion_password", properties.getAuth().getUsuSecundarioProduccionPassword());

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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empresa", properties.toCompanyPayload());
        payload.put("guia", request.getGuia());
        payload.put("items", request.getItems());
        payload.put("token", request.getToken());

        try {
            return guideRemissionRestClient.post()
                    .uri("/2_envio_xml_recibe_ticket.php")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
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
        form.add("ruc", properties.getCompany().getRuc());
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
