package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePricesInfo;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductPurchasePricesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductPurchasePriceRestController {

    private final GetProductPurchasePricesUseCase getProductPurchasePricesUseCase;

    @GetMapping("/{productId}/purchase-prices")
    public ResponseEntity<SuccessResponse<ProductPurchasePricesInfo>> getPurchasePrices(
            @PathVariable Long productId
    ) {
        ProductPurchasePricesInfo response = getProductPurchasePricesUseCase.getByProductId(productId);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
