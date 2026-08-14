package com.paulfernandosr.possystembackend.mycustomers.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.mycustomers.application.MyCustomersService;
import com.paulfernandosr.possystembackend.mycustomers.infrastructure.adapter.input.dto.MyCustomersDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/my-customers")
@RequiredArgsConstructor
public class MyCustomersRestController {
    private final MyCustomersService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<MyCustomerRowResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String lastContact,
            @RequestParam(required = false) String followup,
            @RequestParam(required = false) Long responsibleUserId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                service.list(page, size, search, status, district, tag, lastContact, followup, responsibleUserId, authentication)
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<SuccessResponse<MyCustomersSummaryResponse>> summary(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String lastContact,
            @RequestParam(required = false) String followup,
            @RequestParam(required = false) Long responsibleUserId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                service.summary(search, status, district, tag, lastContact, followup, responsibleUserId, authentication)
        ));
    }

    @GetMapping("/tags")
    public ResponseEntity<SuccessResponse<List<TagResponse>>> tags() {
        return ResponseEntity.ok(SuccessResponse.ok(service.tags()));
    }

    @GetMapping("/materials")
    public ResponseEntity<SuccessResponse<List<MaterialResponse>>> materials() {
        return ResponseEntity.ok(SuccessResponse.ok(service.materials()));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<SuccessResponse<MyCustomerDetailResponse>> detail(
            @PathVariable Long customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.detail(customerId, authentication)));
    }

    @GetMapping("/{customerId}/activity")
    public ResponseEntity<SuccessResponse<PageResponse<ActivityResponse>>> activity(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.activity(customerId, page, size, authentication)));
    }

    @GetMapping("/{customerId}/whatsapp")
    public ResponseEntity<SuccessResponse<List<WhatsAppMessageResponse>>> whatsapp(
            @PathVariable Long customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.whatsapp(customerId, authentication)));
    }

    @GetMapping("/{customerId}/offers")
    public ResponseEntity<SuccessResponse<List<OfferHistoryResponse>>> offers(
            @PathVariable Long customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.offers(customerId, authentication)));
    }

    @GetMapping("/{customerId}/followups")
    public ResponseEntity<SuccessResponse<List<FollowupResponse>>> followups(
            @PathVariable Long customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.followups(customerId, authentication)));
    }

    @PostMapping("/{customerId}/followups")
    public ResponseEntity<SuccessResponse<FollowupResponse>> createFollowup(
            @PathVariable Long customerId,
            @RequestBody FollowupRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.createFollowup(customerId, request, authentication)));
    }

    @PatchMapping("/{customerId}/followups/{followupId}/complete")
    public ResponseEntity<SuccessResponse<FollowupResponse>> completeFollowup(
            @PathVariable Long customerId,
            @PathVariable Long followupId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.completeFollowup(customerId, followupId, authentication)));
    }

    @PatchMapping("/{customerId}/commercial-status")
    public ResponseEntity<SuccessResponse<MyCustomerRowResponse>> updateCommercialStatus(
            @PathVariable Long customerId,
            @RequestBody CommercialStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.updateCommercialStatus(customerId, request, authentication)));
    }

    @PostMapping("/{customerId}/tags")
    public ResponseEntity<SuccessResponse<TagResponse>> addTag(
            @PathVariable Long customerId,
            @RequestBody TagRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.addTag(customerId, request, authentication)));
    }

    @DeleteMapping("/{customerId}/tags/{tagId}")
    public ResponseEntity<Void> removeTag(
            @PathVariable Long customerId,
            @PathVariable Long tagId,
            Authentication authentication
    ) {
        service.removeTag(customerId, tagId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{customerId}/whatsapp/text")
    public ResponseEntity<SuccessResponse<SendCommercialResponse>> sendWhatsApp(
            @PathVariable Long customerId,
            @RequestBody WhatsAppTextRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.sendWhatsApp(customerId, request, authentication)));
    }

    @PostMapping("/{customerId}/commercial-materials/send")
    public ResponseEntity<SuccessResponse<SendCommercialResponse>> sendMaterial(
            @PathVariable Long customerId,
            @RequestBody SendMaterialRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.sendMaterial(customerId, request, authentication)));
    }
}
