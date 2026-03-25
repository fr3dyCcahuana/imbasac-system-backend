package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompanyResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDetailResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionNotFoundException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.GetGuideRemissionDetailUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetGuideRemissionDetailService implements GetGuideRemissionDetailUseCase {
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;

    @Override
    @Transactional(readOnly = true)
    public GuideRemissionDetailResponse getBySerieAndNumero(String serie, String numero) {
        GuideRemissionDocument document = guideRemissionRepository
                .findDocument(properties.getCompany().getRuc(), serie, numero)
                .orElseThrow(() -> new GuideRemissionNotFoundException(
                        "No se encontró la guía de remisión " + serie + "-" + numero + "."
                ));

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
