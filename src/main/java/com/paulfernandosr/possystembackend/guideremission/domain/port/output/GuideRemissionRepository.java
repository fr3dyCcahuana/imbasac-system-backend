package com.paulfernandosr.possystembackend.guideremission.domain.port.output;

import com.paulfernandosr.possystembackend.guideremission.domain.*;

import java.util.Optional;

public interface GuideRemissionRepository {
    void saveSubmission(GuideRemissionCompany company, GuideRemissionSubmission request, GuideRemissionSubmissionResponse response);

    void saveTicketStatus(String companyRuc, GuideRemissionTicketQuery request, GuideRemissionTicketStatusResponse response);

    Optional<GuideRemissionDocument> findDocument(String companyRuc, String serie, String numero);

    GuideRemissionPageResult searchPage(String companyRuc, GuideRemissionPageCriteria criteria);
}
