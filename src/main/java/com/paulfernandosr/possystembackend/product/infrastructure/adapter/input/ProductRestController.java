package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductSalesDetail;
import com.paulfernandosr.possystembackend.product.domain.port.input.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductRestController {

    private final CreateNewProductUseCase createNewProductUseCase;
    private final GetPageOfProductsUseCase getPageOfProductsUseCase;
    private final GetProductInfoUseCase getProductInfoUseCase;
    private final UpdateProductInfoUseCase updateProductInfoUseCase;
    private final GetProductSalesDetailPageUseCase getProductSalesDetailPageUseCase;
    // POST /products
    @PostMapping
    public ResponseEntity<SuccessResponse<Product>> createNewProduct(
            @Valid @RequestBody Product product
    ) {
        Product createdProduct = createNewProductUseCase.createNewProduct(product);

        // Opcional: Location header con la URL del recurso creado
        URI location = URI.create("/products/" + createdProduct.getId());

        return ResponseEntity
                .created(location) // HTTP 201 + Location
                .body(SuccessResponse.ok(createdProduct));
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

    @GetMapping("/sales-detail")
    public ResponseEntity<SuccessResponse<Collection<ProductSalesDetail>>> getSalesDetailPage(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "false") boolean onlyWithStock,
            @RequestParam(defaultValue = "A") String priceList,
            @RequestParam(defaultValue = "PROFORMA") String context,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        Page<ProductSalesDetail> result = getProductSalesDetailPageUseCase.getPage(
                query, category, onlyWithStock, priceList, context,
                new Pageable(page, size)
        );

        SuccessResponse.Metadata metadata = PageMapper.mapPage(result);
        return ResponseEntity.ok(SuccessResponse.ok(result.getContent(), metadata));
    }
}
