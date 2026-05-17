package com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WhatsAppCampaignCreateRequest {
    @NotBlank
    private String name;
    private String description;

    /** Nombre exacto de la plantilla aprobada en Meta. */
    @NotBlank
    private String templateName;

    /** Ej.: es, es_PE, en_US. */
    private String languageCode = "es";

    /** URL HTTPS pública de la imagen promocional, si la plantilla tiene header IMAGE. */
    private String imageUrl;

    /** Variables del cuerpo de la plantilla, en orden: {{1}}, {{2}}, etc. */
    private List<String> bodyParameters;

    /** Confirmación operativa de que los destinatarios aceptaron recibir promociones. */
    @NotNull
    private Boolean confirmPolicyCompliance;

    private String createdBy;

    @Valid
    private WhatsAppCampaignTargetRequest target = new WhatsAppCampaignTargetRequest();
}
