package com.paulfernandosr.possystembackend.guideremission.domain.port.output;

import com.paulfernandosr.possystembackend.guideremission.domain.*;

import java.util.Optional;

public interface GuideRemissionRepository {
    GuideRemissionDocument saveDraft(GuideRemissionCompany company, GuideRemissionFullFlowRequest request);

    GuideRemissionDocument updateDraft(GuideRemissionCompany company, String serie, String numero, GuideRemissionFullFlowRequest request);

    void saveSubmission(GuideRemissionCompany company, GuideRemissionSubmission request, GuideRemissionSubmissionResponse response);

    void saveTicketStatus(String companyRuc, GuideRemissionTicketQuery request, GuideRemissionTicketStatusResponse response);

    void markEmissionError(String companyRuc, String serie, String numero, GuideRemissionStatus status, String message);

    Optional<GuideRemissionDocument> findDocument(String companyRuc, String serie, String numero);

    GuideRemissionGeneratedSeries reserveNextGuideRemissionSeries();

    GuideRemissionPageResult searchPage(String companyRuc, GuideRemissionPageCriteria criteria);
}
