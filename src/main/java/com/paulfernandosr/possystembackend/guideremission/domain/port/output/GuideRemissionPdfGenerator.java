package com.paulfernandosr.possystembackend.guideremission.domain.port.output;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompany;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;

public interface GuideRemissionPdfGenerator {
    byte[] generate(GuideRemissionCompany company, GuideRemissionDocument document);
}
