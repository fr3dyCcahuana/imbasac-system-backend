package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmission;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.SubmitGuideRemissionUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmitGuideRemissionService implements SubmitGuideRemissionUseCase {
    private final GuideRemissionRequestNormalizer normalizer;
    private final GuideRemissionBusinessValidator validator;
    private final GuideRemissionProvider guideRemissionProvider;
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionPhpResponseEvaluator responseEvaluator;

    @Override
    @Transactional
    public GuideRemissionSubmissionResponse submit(GuideRemissionSubmission request) {
        normalizer.normalize(request);
        validator.validate(request);

        GuideRemissionSubmissionResponse response = guideRemissionProvider.submit(request);
        responseEvaluator.assertSuccessfulSubmission(response);
        guideRemissionRepository.saveSubmission(properties.toCompanyPayload(), request, response);
        return response;
    }
}
