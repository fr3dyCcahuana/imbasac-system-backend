package com.paulfernandosr.possystembackend.guideremission.domain.port.output;

import com.paulfernandosr.possystembackend.guideremission.domain.*;

public interface GuideRemissionProvider {
    GuideRemissionTokenResponse requestToken(GuideRemissionTokenRequest request);

    GuideRemissionSubmissionResponse submit(GuideRemissionSubmission request);

    GuideRemissionTicketStatusResponse queryTicket(GuideRemissionTicketQuery request);
}
