package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexTrendResponse;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductKardexTrendUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/{productId}/kardex/trend")
public class ProductKardexTrendRestController {

    private final GetProductKardexTrendUseCase getProductKardexTrendUseCase;

    @GetMapping
    public ResponseEntity<SuccessResponse<ProductKardexTrendResponse>> getTrend(
            @PathVariable Long productId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,

            // DAY | MONTH | YEAR. Default: MONTH
            @RequestParam(defaultValue = "MONTH") String groupBy,

            // ALL | PURCHASE | SALE | COUNTER_SALE | ADJUSTMENT. Default: ALL
            @RequestParam(defaultValue = "ALL") String source,

            // IN_PURCHASE | OUT_SALE | OUT_COUNTER_SALE | IN_COUNTER_SALE_VOID | IN_RETURN | IN_SALE_EDIT | OUT_SALE_EDIT | IN_ADJUST | OUT_ADJUST
            @RequestParam(required = false) String movementType,

            // true: incluye periodos sin movimiento con cantidades 0. Default: true
            @RequestParam(defaultValue = "true") Boolean includeEmptyPeriods
    ) {
        ProductKardexTrendResponse response = getProductKardexTrendUseCase.getTrend(
                productId,
                dateFrom,
                dateTo,
                groupBy,
                source,
                movementType,
                includeEmptyPeriods
        );

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
