package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductVehicleSpecsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpsertProductVehicleSpecsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/{productId}/vehicle-specs")
public class ProductVehicleSpecsRestController {

    private final UpsertProductVehicleSpecsUseCase upsertUseCase;
    private final GetProductVehicleSpecsUseCase getUseCase;

    // GET /products/{productId}/vehicle-specs
    @GetMapping
    public ResponseEntity<SuccessResponse<ProductVehicleSpecs>> get(
            @PathVariable Long productId
    ) {
        ProductVehicleSpecs specs = getUseCase.getByProductId(productId);
        return ResponseEntity.ok(SuccessResponse.ok(specs));
    }

    // PUT /products/{productId}/vehicle-specs
    @PutMapping
    public ResponseEntity<Void> upsert(
            @PathVariable Long productId,
            @Valid @RequestBody ProductVehicleSpecs specs
    ) {
        upsertUseCase.upsert(productId, specs);
        return ResponseEntity.noContent().build();
    }
}
