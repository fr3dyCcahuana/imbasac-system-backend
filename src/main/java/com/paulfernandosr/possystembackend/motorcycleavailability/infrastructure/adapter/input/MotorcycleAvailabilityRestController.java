package com.paulfernandosr.possystembackend.motorcycleavailability.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.motorcycleavailability.application.MotorcycleAvailabilityService;
import com.paulfernandosr.possystembackend.motorcycleavailability.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/motorcycle-availability")
public class MotorcycleAvailabilityRestController {

    private final MotorcycleAvailabilityService motorcycleAvailabilityService;

    @GetMapping
    public ResponseEntity<SuccessResponse<MotorcycleAvailabilityResponse>> findPage(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String brand,
            @RequestParam(defaultValue = "") String engineCapacity,
            @RequestParam(defaultValue = "") String warehouseLocation,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        MotorcycleAvailabilityPage result = motorcycleAvailabilityService.findPage(
                MotorcycleAvailabilityQuery.builder()
                        .query(query)
                        .brand(brand)
                        .engineCapacity(engineCapacity)
                        .warehouseLocation(warehouseLocation)
                        .status(status)
                        .page(page)
                        .size(size)
                        .build()
        );

        SuccessResponse.Metadata metadata = SuccessResponse.Metadata.builder()
                .pageNumber(result.getPage())
                .pageSize(result.getSize())
                .numberOfElements(result.getNumberOfElements())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();

        return ResponseEntity.ok(SuccessResponse.ok(result.getResponse(), metadata));
    }

    @GetMapping("/filter-options")
    public ResponseEntity<SuccessResponse<MotorcycleAvailabilityFilterOptions>> filterOptions() {
        return ResponseEntity.ok(SuccessResponse.ok(motorcycleAvailabilityService.findFilterOptions()));
    }

    @GetMapping("/{productId}/units")
    public ResponseEntity<SuccessResponse<MotorcycleAvailabilityUnitsResponse>> findUnits(@PathVariable Long productId) {
        return ResponseEntity.ok(SuccessResponse.ok(motorcycleAvailabilityService.findUnits(productId)));
    }

    @GetMapping("/contract-prefill")
    public ResponseEntity<SuccessResponse<MotorcycleContractPrefillResponse>> contractPrefill(
            @RequestParam Long productId,
            @RequestParam Long serialUnitId,
            @RequestParam(defaultValue = "A") String priceList
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                motorcycleAvailabilityService.contractPrefill(productId, serialUnitId, priceList)
        ));
    }
}
