package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateNewProductUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetPageOfProductsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductInfoUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpdateProductInfoUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductRestController {

    private final CreateNewProductUseCase createNewProductUseCase;
    private final GetPageOfProductsUseCase getPageOfProductsUseCase;
    private final GetProductInfoUseCase getProductInfoUseCase;
    private final UpdateProductInfoUseCase updateProductInfoUseCase;

    // POST /products
    @PostMapping
    public ResponseEntity<SuccessResponse<Void>> createNewProduct(@Valid @RequestBody Product product) {
        createNewProductUseCase.createNewProduct(product);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    // GET /products?query=&page=0&size=10
    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Product>>> getPageOfProducts(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<Product> pageOfProducts = getPageOfProductsUseCase.getPageOfProducts(query, new Pageable(page, size));
        SuccessResponse.Metadata metadata = PageMapper.mapPage(pageOfProducts);
        return ResponseEntity.ok(SuccessResponse.ok(pageOfProducts.getContent(), metadata));
    }

    // GET /products/{productId}
    @GetMapping("/{productId}")
    public ResponseEntity<SuccessResponse<Product>> getProductInfo(@PathVariable Long productId) {
        Product product = getProductInfoUseCase.getProductInfoById(productId);
        return ResponseEntity.ok(SuccessResponse.ok(product));
    }

    // PUT /products/{productId}
    @PutMapping("/{productId}")
    public ResponseEntity<Void> updateProductInfo(
            @PathVariable Long productId,
            @Valid @RequestBody Product product
    ) {
        updateProductInfoUseCase.updateProductInfoById(productId, product);
        return ResponseEntity.noContent().build();
    }
}
