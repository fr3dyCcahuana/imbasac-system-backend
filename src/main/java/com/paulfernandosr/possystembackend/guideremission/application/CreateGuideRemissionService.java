package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDetailResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionFullFlowRequest;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.CreateGuideRemissionUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateGuideRemissionService implements CreateGuideRemissionUseCase {
    private final GuideRemissionRequestNormalizer normalizer;
    private final GuideRemissionBusinessValidator validator;
    private final GuideRemissionSeriesGeneratorService seriesGeneratorService;
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionDetailAssembler detailAssembler;

    @Override
    @Transactional
    public GuideRemissionDetailResponse create(GuideRemissionFullFlowRequest request) {
        normalizer.normalize(request);
        validator.validate(request);
        seriesGeneratorService.assignNextSeriesAndNumber(request.getGuia());
        GuideRemissionDocument document = guideRemissionRepository.saveDraft(properties.toCompanyPayload(), request);
        return detailAssembler.toResponse(document);
    }
}
