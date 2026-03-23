package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketQuery;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;

public interface QueryGuideRemissionTicketUseCase {
    GuideRemissionTicketStatusResponse queryTicket(GuideRemissionTicketQuery request);
}
