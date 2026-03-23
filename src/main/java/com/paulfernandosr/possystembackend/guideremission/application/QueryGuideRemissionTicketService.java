package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketQuery;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.QueryGuideRemissionTicketUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryGuideRemissionTicketService implements QueryGuideRemissionTicketUseCase {
    private final GuideRemissionProvider guideRemissionProvider;

    @Override
    public GuideRemissionTicketStatusResponse queryTicket(GuideRemissionTicketQuery request) {
        return guideRemissionProvider.queryTicket(request);
    }
}
