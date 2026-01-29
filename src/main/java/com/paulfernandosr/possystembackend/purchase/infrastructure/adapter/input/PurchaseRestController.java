package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.purchase.domain.Purchase;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CancelPurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.CreatePurchaseUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.GetPageOfPurchasesUseCase;
import com.paulfernandosr.possystembackend.purchase.domain.port.input.GetPurchaseDetailUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseRestController {

    private final CreatePurchaseUseCase createPurchaseUseCase;
    private final GetPageOfPurchasesUseCase getPageOfPurchasesUseCase;
    private final GetPurchaseDetailUseCase getPurchaseDetailUseCase;
    private final CancelPurchaseUseCase cancelPurchaseUseCase;

    // POST /purchases
    @PostMapping
    public ResponseEntity<SuccessResponse<Purchase>> createPurchase(
            @Valid @RequestBody Purchase purchase,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        Purchase created = createPurchaseUseCase.createPurchase(purchase, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(created));
    }

    // GET /purchases
    @GetMapping
    public ResponseEntity<SuccessResponse<Page<Purchase>>> getPageOfPurchases(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<Purchase> purchases = getPageOfPurchasesUseCase.getPageOfPurchases(query, new Pageable(page, size));
        return ResponseEntity.ok(SuccessResponse.ok(purchases));
    }

    // GET /purchases/{purchaseId}
    @GetMapping("/{purchaseId}")
    public ResponseEntity<SuccessResponse<Purchase>> getPurchaseDetail(
            @PathVariable Long purchaseId
    ) {
        Purchase purchase = getPurchaseDetailUseCase.getPurchaseById(purchaseId);
        return ResponseEntity.ok(SuccessResponse.ok(purchase));
    }

    // PUT /purchases/{purchaseId}/cancel
    @PutMapping("/{purchaseId}/cancel")
    public ResponseEntity<Void> cancelPurchase(
            @PathVariable Long purchaseId,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        cancelPurchaseUseCase.cancelPurchaseById(purchaseId, username);
        return ResponseEntity.noContent().build();
    }
}
