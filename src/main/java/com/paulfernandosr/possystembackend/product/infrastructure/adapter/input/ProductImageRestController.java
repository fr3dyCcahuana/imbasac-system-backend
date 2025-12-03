package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductImage;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateProductImageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.DeleteProductImageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductImagesUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpdateProductImageUseCase;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.ProductImageFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/{productId}/images")
public class ProductImageRestController {

    private final CreateProductImageUseCase createProductImageUseCase;
    private final GetProductImagesUseCase getProductImagesUseCase;
    private final UpdateProductImageUseCase updateProductImageUseCase;
    private final DeleteProductImageUseCase deleteProductImageUseCase;
    private final ProductImageFileStorageService fileStorageService;

    // POST /products/{productId}/images
    // Sube archivo y registra ruta en BD
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createProductImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "position", required = false, defaultValue = "1") Short position,
            @RequestParam(value = "isMain", required = false, defaultValue = "false") Boolean isMain
    ) {
        String imageUrl = fileStorageService.store(productId, file);

        ProductImage image = ProductImage.builder()
                .imageUrl(imageUrl)
                .position(position)
                .isMain(isMain)
                .build();

        createProductImageUseCase.createProductImage(productId, image);
        return ResponseEntity.status(201).build();
    }

    // GET /products/{productId}/images
    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<ProductImage>>> getProductImages(
            @PathVariable Long productId
    ) {
        Collection<ProductImage> images = getProductImagesUseCase.getImagesByProductId(productId);
        return ResponseEntity.ok(SuccessResponse.ok(images));
    }

    // PUT /products/{productId}/images/{imageId}
    // Permite cambiar metadata y opcionalmente reemplazar el archivo
    @PutMapping(
            value = "/{imageId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> updateProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "position", required = false) Short position,
            @RequestParam(value = "isMain", required = false) Boolean isMain
    ) {
        String imageUrl = null;

        if (file != null && !file.isEmpty()) {
            imageUrl = fileStorageService.store(productId, file);
        }

        ProductImage image = ProductImage.builder()
                .imageUrl(imageUrl)   // puede ser null si no se reemplaza archivo
                .position(position)
                .isMain(isMain)
                .build();

        updateProductImageUseCase.updateProductImage(productId, imageId, image);
        return ResponseEntity.noContent().build();
    }

    // DELETE /products/{productId}/images/{imageId}
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        deleteProductImageUseCase.deleteProductImage(productId, imageId);
        // (Opcional: aquí también podrías borrar el archivo físico con otro método del storage)
        return ResponseEntity.noContent().build();
    }
}
