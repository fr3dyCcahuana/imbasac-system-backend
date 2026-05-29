package com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.productoffer.application.ProductBasicOfferService;
import com.paulfernandosr.possystembackend.productoffer.domain.ProductBasicOffer;
import com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto.ProductBasicOfferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/product-offers")
public class ProductBasicOfferRestController {

    private final ProductBasicOfferService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<ProductBasicOffer>>> findAll(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.findAll(status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<ProductBasicOffer>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<ProductBasicOffer>> create(
            @RequestBody ProductBasicOfferRequest request
    ) {
        ProductBasicOffer created = service.create(request);
        return ResponseEntity
                .created(URI.create("/product-offers/" + created.id()))
                .body(SuccessResponse.created(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<ProductBasicOffer>> update(
            @PathVariable Long id,
            @RequestBody ProductBasicOfferRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
