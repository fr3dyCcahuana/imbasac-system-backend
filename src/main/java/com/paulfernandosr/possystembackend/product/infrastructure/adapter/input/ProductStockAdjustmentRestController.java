package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustmentCommand;
import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustmentResult;
import com.paulfernandosr.possystembackend.product.domain.port.input.AdjustProductStockUseCase;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockAdjustmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductStockAdjustmentRestController {

    private final AdjustProductStockUseCase useCase;

    @PostMapping("/{productId}/stock-adjustments")
    public ResponseEntity<SuccessResponse<ProductStockAdjustmentResult>> adjust(
            @PathVariable Long productId,
            @Valid @RequestBody ProductStockAdjustmentRequest request
    ) {

        List<ProductSerialUnit> serialUnits = mapSerialUnits(request);

        ProductStockAdjustmentCommand command = ProductStockAdjustmentCommand.builder()
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .unitCost(request.getUnitCost())
                .reason(request.getReason())
                .note(request.getNote())
                .serialUnits(serialUnits)
                .build();

        ProductStockAdjustmentResult result = useCase.adjust(productId, command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.ok(result));
    }

    private List<ProductSerialUnit> mapSerialUnits(ProductStockAdjustmentRequest request) {
        if (request.getSerialUnits() == null) {
            return Collections.emptyList();
        }
        return request.getSerialUnits().stream()
                .map(u -> ProductSerialUnit.builder()
                        .id(u.getId())
                        .vin(u.getVin())
                        .chassisNumber(u.getChassisNumber())
                        .engineNumber(u.getEngineNumber())
                        .color(u.getColor())
                        .yearMake(u.getYearMake() != null ? u.getYearMake() : null)
                        .duaNumber(u.getDuaNumber())
                        .duaItem(u.getDuaItem())
                        .build())
                .collect(Collectors.toList());
    }
}
