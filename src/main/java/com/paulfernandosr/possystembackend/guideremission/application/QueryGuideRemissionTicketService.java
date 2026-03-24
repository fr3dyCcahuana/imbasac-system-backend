package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketQuery;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTicketStatusResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.QueryGuideRemissionTicketUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueryGuideRemissionTicketService implements QueryGuideRemissionTicketUseCase {
    private final GuideRemissionProvider guideRemissionProvider;
    private final GuideRemissionRepository guideRemissionRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionPhpResponseEvaluator responseEvaluator;

    @Override
    @Transactional
    public GuideRemissionTicketStatusResponse queryTicket(GuideRemissionTicketQuery request) {
        GuideRemissionTicketStatusResponse response = guideRemissionProvider.queryTicket(request);
        responseEvaluator.assertSuccessfulTicketQuery(response);
        guideRemissionRepository.saveTicketStatus(properties.getCompany().getRuc(), request, response);
        return response;
    }
}
