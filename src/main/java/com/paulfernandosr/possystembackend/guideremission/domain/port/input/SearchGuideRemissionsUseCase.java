package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionPageCriteria;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionPageResponse;

public interface SearchGuideRemissionsUseCase {
    GuideRemissionPageResponse search(GuideRemissionPageCriteria criteria);
}
