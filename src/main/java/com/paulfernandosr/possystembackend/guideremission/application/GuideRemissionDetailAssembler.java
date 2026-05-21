package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompanyResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDetailResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuideRemissionDetailAssembler {
    private final GuideRemissionProperties properties;

    public GuideRemissionDetailResponse toResponse(GuideRemissionDocument document) {
        return GuideRemissionDetailResponse.builder()
                .company(GuideRemissionCompanyResponse.builder()
                        .ruc(properties.getCompany().getRuc())
                        .razonSocial(properties.getCompany().getRazonSocial())
                        .nombreComercial(properties.getCompany().getNombreComercial())
                        .domicilioFiscal(properties.getCompany().getDomicilioFiscal())
                        .ubigeo(properties.getCompany().getUbigeo())
                        .urbanizacion(properties.getCompany().getUrbanizacion())
                        .distrito(properties.getCompany().getDistrito())
                        .provincia(properties.getCompany().getProvincia())
                        .departamento(properties.getCompany().getDepartamento())
                        .modo(properties.getCompany().getModo())
                        .build())
                .guideRemission(document)
                .build();
    }
}
