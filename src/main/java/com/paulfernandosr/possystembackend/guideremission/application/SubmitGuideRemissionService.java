package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmission;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.SubmitGuideRemissionUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmitGuideRemissionService implements SubmitGuideRemissionUseCase {
    private final GuideRemissionBusinessValidator validator;
    private final GuideRemissionProvider guideRemissionProvider;

    @Override
    public GuideRemissionSubmissionResponse submit(GuideRemissionSubmission request) {
        validator.validate(request);
        return guideRemissionProvider.submit(request);
    }
}
