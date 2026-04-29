package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexEntry;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductKardexPageUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/kardex")
public class ProductKardexRestController {

    private final GetProductKardexPageUseCase getProductKardexPageUseCase;

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<ProductKardexEntry>>> getKardexPage(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String brand,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "") String movementType,

            // ALL | ENTRADA | SALIDA
            @RequestParam(defaultValue = "ALL") String direction,

            // ALL | PURCHASE | SALE | COUNTER_SALE | ADJUSTMENT
            @RequestParam(defaultValue = "ALL") String source,

            @RequestParam(defaultValue = "") String docType,
            @RequestParam(defaultValue = "") String series,
            @RequestParam(defaultValue = "") String number,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductKardexEntry> result = getProductKardexPageUseCase.getPage(
                query,
                productId,
                category,
                brand,
                model,
                movementType,
                direction,
                source,
                docType,
                series,
                number,
                dateFrom,
                dateTo,
                new Pageable(page, size)
        );

        SuccessResponse.Metadata metadata = PageMapper.mapPage(result);
        return ResponseEntity.ok(SuccessResponse.ok(result.getContent(), metadata));
    }
}
