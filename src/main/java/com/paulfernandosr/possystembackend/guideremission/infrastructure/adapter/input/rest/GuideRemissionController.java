package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.input.rest;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.QueryGuideRemissionTicketUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.RequestGuideRemissionTokenUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.SubmitGuideRemissionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guide-remissions")
@RequiredArgsConstructor
public class GuideRemissionController {
    private final RequestGuideRemissionTokenUseCase requestGuideRemissionTokenUseCase;
    private final SubmitGuideRemissionUseCase submitGuideRemissionUseCase;
    private final QueryGuideRemissionTicketUseCase queryGuideRemissionTicketUseCase;

    @PostMapping("/token")
    public ResponseEntity<GuideRemissionTokenResponse> requestToken(@Valid @RequestBody GuideRemissionTokenRequest request) {
        return ResponseEntity.ok(requestGuideRemissionTokenUseCase.requestToken(request));
    }

    @PostMapping("/submit")
    public ResponseEntity<GuideRemissionSubmissionResponse> submit(@Valid @RequestBody GuideRemissionSubmission request) {
        return ResponseEntity.ok(submitGuideRemissionUseCase.submit(request));
    }

    @PostMapping("/ticket-status")
    public ResponseEntity<GuideRemissionTicketStatusResponse> queryTicket(@Valid @RequestBody GuideRemissionTicketQuery request) {
        return ResponseEntity.ok(queryGuideRemissionTicketUseCase.queryTicket(request));
    }
}
