package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionNotFoundException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.GenerateGuideRemissionPdfUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionPdfGenerator;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GenerateGuideRemissionPdfService implements GenerateGuideRemissionPdfUseCase {
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionPdfGenerator guideRemissionPdfGenerator;
    private final GuideRemissionProperties properties;

    @Override
    @Transactional(readOnly = true)
    public byte[] generate(String serie, String numero) {
        GuideRemissionDocument document = guideRemissionRepository
                .findDocument(properties.getCompany().getRuc(), serie, numero)
                .orElseThrow(() -> new GuideRemissionNotFoundException(
                        "No se encontró la guía de remisión " + serie + "-" + numero + "."
                ));

        return guideRemissionPdfGenerator.generate(properties.toCompanyPayload(), document);
    }
}
