package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.application.importer.CompetitiveImportTemplateGenerator;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetCompetitiveImportTemplateUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.BrandWriteRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.CategoryWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCompetitiveImportTemplateService implements GetCompetitiveImportTemplateUseCase {

    private final CategoryWriteRepository categoryWriteRepository;
    private final BrandWriteRepository brandWriteRepository;

    @Override
    public byte[] generateTemplate() {
        return CompetitiveImportTemplateGenerator.generate(
                categoryWriteRepository.findAllNames(),
                brandWriteRepository.findAllNames()
        );
    }
}
