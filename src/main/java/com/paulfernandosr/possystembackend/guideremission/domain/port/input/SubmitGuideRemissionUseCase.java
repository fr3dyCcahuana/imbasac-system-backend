package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmission;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;

public interface SubmitGuideRemissionUseCase {
    GuideRemissionSubmissionResponse submit(GuideRemissionSubmission request);
}
