package com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.landing.application.LandingContentService;
import com.paulfernandosr.possystembackend.landing.application.LandingOrderService;
import com.paulfernandosr.possystembackend.landing.application.PromotionSubmissionService;
import com.paulfernandosr.possystembackend.landing.domain.LandingContent;
import com.paulfernandosr.possystembackend.landing.domain.LandingOrder;
import com.paulfernandosr.possystembackend.landing.domain.PromotionSubmission;
import com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto.LandingOrderRequest;
import com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto.PromotionSubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/landing")
public class LandingRestController {

    private final LandingContentService landingContentService;
    private final LandingOrderService landingOrderService;
    private final PromotionSubmissionService promotionSubmissionService;

    @GetMapping
    public ResponseEntity<SuccessResponse<LandingContent>> getLandingContent() {
        return ResponseEntity.ok(SuccessResponse.ok(landingContentService.getContent()));
    }

    @PostMapping("/orders")
    public ResponseEntity<SuccessResponse<LandingOrder>> createOrder(
            @RequestBody LandingOrderRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.created(landingOrderService.create(request)));
    }

    @PostMapping("/promotions")
    public ResponseEntity<SuccessResponse<PromotionSubmission>> createPromotionSubmission(
            @RequestBody PromotionSubmissionRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.created(promotionSubmissionService.create(request)));
    }
}
