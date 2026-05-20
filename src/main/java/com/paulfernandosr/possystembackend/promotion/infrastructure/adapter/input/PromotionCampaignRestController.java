package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.promotion.application.PromotionCampaignService;
import com.paulfernandosr.possystembackend.promotion.domain.PromotionCampaign;
import com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input.dto.PromotionCampaignRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/promotions")
public class PromotionCampaignRestController {

    private final PromotionCampaignService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<PromotionCampaign>>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.findAll(status, kind)));
    }

    @GetMapping("/active")
    public ResponseEntity<SuccessResponse<List<PromotionCampaign>>> findActiveForLanding() {
        return ResponseEntity.ok(SuccessResponse.ok(service.findActiveForLanding()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<PromotionCampaign>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<PromotionCampaign>> create(
            @RequestBody PromotionCampaignRequest request
    ) {
        PromotionCampaign created = service.create(request);
        return ResponseEntity
                .created(URI.create("/promotions/" + created.id()))
                .body(SuccessResponse.created(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<PromotionCampaign>> update(
            @PathVariable Long id,
            @RequestBody PromotionCampaignRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
