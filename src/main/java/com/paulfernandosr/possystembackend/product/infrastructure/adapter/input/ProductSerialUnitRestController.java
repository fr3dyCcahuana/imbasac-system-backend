package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateProductSerialUnitUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetPageOfProductSerialUnitsUseCase;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/{productId}/serial-units")
public class ProductSerialUnitRestController {

    private final CreateProductSerialUnitUseCase createProductSerialUnitUseCase;
    private final GetPageOfProductSerialUnitsUseCase getPageOfProductSerialUnitsUseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<ProductSerialUnit>> create(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductSerialUnitRequest body
    ) {
        ProductSerialUnit unit = ProductSerialUnit.builder()
                .vin(body.getVin())
                .chassisNumber(body.getChassisNumber())
                .engineNumber(body.getEngineNumber())
                .color(body.getColor())
                .yearMake(body.getYearMake())
                .duaNumber(body.getDuaNumber())
                .duaItem(body.getDuaItem())
                .status(body.getStatus())
                .purchaseItemId(body.getPurchaseItemId())
                .build();

        ProductSerialUnit created = createProductSerialUnitUseCase.create(productId, unit);
        URI location = URI.create("/products/" + productId + "/serial-units/" + created.getId());
        return ResponseEntity.created(location).body(SuccessResponse.ok(created));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<ProductSerialUnit>>> getPage(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ProductSerialUnit> result = getPageOfProductSerialUnitsUseCase.getPage(
                productId,
                query,
                status,
                new Pageable(page, size)
        );

        SuccessResponse.Metadata metadata = PageMapper.mapPage(result);
        return ResponseEntity.ok(SuccessResponse.ok(result.getContent(), metadata));
    }

    @Getter
    @Setter
    public static class CreateProductSerialUnitRequest {
        private Long purchaseItemId;

        private String vin;
        private String chassisNumber;
        private String engineNumber;

        private String color;
        private Short yearMake;
        private String duaNumber;
        private Integer duaItem;

        private String status;       // opcional (default EN_ALMACEN)
    }
}
