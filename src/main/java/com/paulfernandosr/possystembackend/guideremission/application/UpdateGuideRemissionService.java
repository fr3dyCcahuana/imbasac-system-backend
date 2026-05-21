package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDetailResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionFullFlowRequest;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.UpdateGuideRemissionUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateGuideRemissionService implements UpdateGuideRemissionUseCase {
    private final GuideRemissionRequestNormalizer normalizer;
    private final GuideRemissionBusinessValidator validator;
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionDetailAssembler detailAssembler;

    @Override
    @Transactional
    public GuideRemissionDetailResponse update(String serie, String numero, GuideRemissionFullFlowRequest request) {
        normalizer.normalize(request);
        validator.validate(request);
        GuideRemissionDocument document = guideRemissionRepository.updateDraft(properties.toCompanyPayload(), serie, numero, request);
        return detailAssembler.toResponse(document);
    }
}
