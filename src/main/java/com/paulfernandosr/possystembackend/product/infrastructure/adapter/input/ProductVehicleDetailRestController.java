package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductVehicleDetailUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpsertProductVehicleDetailUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/{productId}/vehicle-detail")
public class ProductVehicleDetailRestController {

    private final UpsertProductVehicleDetailUseCase upsertProductVehicleDetailUseCase;
    private final GetProductVehicleDetailUseCase getProductVehicleDetailUseCase;

    // GET /products/{productId}/vehicle-detail
    @GetMapping
    public ResponseEntity<SuccessResponse<ProductVehicleDetail>> getVehicleDetail(
            @PathVariable Long productId
    ) {
        ProductVehicleDetail detail = getProductVehicleDetailUseCase.getVehicleDetailByProductId(productId);
        return ResponseEntity.ok(SuccessResponse.ok(detail));
    }

    // PUT /products/{productId}/vehicle-detail
    @PutMapping
    public ResponseEntity<Void> upsertVehicleDetail(
            @PathVariable Long productId,
            @Valid @RequestBody ProductVehicleDetail detail
    ) {
        upsertProductVehicleDetailUseCase.upsertVehicleDetail(productId, detail);
        return ResponseEntity.noContent().build();
    }
}
