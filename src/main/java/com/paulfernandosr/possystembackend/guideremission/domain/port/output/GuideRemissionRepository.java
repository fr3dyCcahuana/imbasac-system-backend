package com.paulfernandosr.possystembackend.guideremission.domain.port.output;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompany;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmission;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionSubmissionResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketQuery;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;

public interface GuideRemissionRepository {
    void saveSubmission(GuideRemissionCompany company, GuideRemissionSubmission request, GuideRemissionSubmissionResponse response);

    void saveTicketStatus(String companyRuc, GuideRemissionTicketQuery request, GuideRemissionTicketStatusResponse response);
}
