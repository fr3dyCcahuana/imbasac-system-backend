package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.customer.application.CustomerMapService;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerMapDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CustomerMapRestController {
    private final CustomerMapService service;

    @GetMapping("/customer-addresses/map/config")
    public ResponseEntity<SuccessResponse<CustomerMapConfigResponse>> config() {
        return ResponseEntity.ok(SuccessResponse.ok(service.config()));
    }

    @GetMapping("/customer-addresses/map/markers")
    public ResponseEntity<SuccessResponse<List<CustomerAddressMarkerResponse>>> markers(@RequestParam Map<String, String> filters) {
        return ResponseEntity.ok(SuccessResponse.ok(service.markers(filters)));
    }

    @GetMapping("/customer-addresses/map/summary")
    public ResponseEntity<SuccessResponse<CustomerMapSummaryResponse>> summary(@RequestParam Map<String, String> filters) {
        return ResponseEntity.ok(SuccessResponse.ok(service.summary(filters)));
    }

    @GetMapping("/customer-addresses/geolocation-pending")
    public ResponseEntity<SuccessResponse<List<CustomerAddressMarkerResponse>>> pending(@RequestParam Map<String, String> filters) {
        return ResponseEntity.ok(SuccessResponse.ok(service.pending(filters)));
    }

    @GetMapping("/customers/{customerId}/addresses/{addressId}/geolocation")
    public ResponseEntity<SuccessResponse<CustomerAddressGeolocationDetailResponse>> detail(@PathVariable Long customerId,
                                                                                            @PathVariable Long addressId) {
        return ResponseEntity.ok(SuccessResponse.ok(service.detail(customerId, addressId)));
    }

    @PatchMapping("/customers/{customerId}/addresses/{addressId}/geolocation")
    public ResponseEntity<SuccessResponse<CustomerAddressGeolocationDetailResponse>> update(@PathVariable Long customerId,
                                                                                            @PathVariable Long addressId,
                                                                                            @RequestBody CustomerAddressGeolocationUpdateRequest request,
                                                                                            Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(service.update(customerId, addressId, request, principal, false)));
    }

    @PostMapping("/customers/{customerId}/addresses/{addressId}/geolocation/verify")
    public ResponseEntity<SuccessResponse<CustomerAddressGeolocationDetailResponse>> verify(@PathVariable Long customerId,
                                                                                            @PathVariable Long addressId,
                                                                                            @RequestBody CustomerAddressGeolocationUpdateRequest request,
                                                                                            Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(service.update(customerId, addressId, request, principal, true)));
    }

    @PostMapping("/customers/{customerId}/addresses/{addressId}/geolocation/suggest")
    public ResponseEntity<SuccessResponse<GeocodingSuggestionResponse>> suggest(@PathVariable Long customerId,
                                                                                @PathVariable Long addressId) {
        return ResponseEntity.ok(SuccessResponse.ok(service.suggest(customerId, addressId)));
    }

    @PostMapping("/customer-addresses/geolocation/batch")
    public ResponseEntity<SuccessResponse<GeolocationBatchResponse>> batch(@RequestBody(required = false) GeolocationBatchRequest request,
                                                                           Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(service.batch(request, principal)));
    }

    @GetMapping("/customer-addresses/geolocation/batch/auto")
    public ResponseEntity<SuccessResponse<GeolocationBatchResponse>> automaticBatchStatus() {
        return ResponseEntity.ok(SuccessResponse.ok(service.automaticBatchStatus()));
    }

    @PostMapping("/customer-addresses/geolocation/batch/auto/run")
    public ResponseEntity<SuccessResponse<GeolocationBatchResponse>> startAutomaticBatch(Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(service.startAutomaticBatchNow(principal)));
    }

    @GetMapping("/customer-addresses/geolocation/batch/{jobId}")
    public ResponseEntity<SuccessResponse<GeolocationBatchResponse>> batchStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(SuccessResponse.ok(GeolocationBatchResponse.builder()
                .jobId(jobId)
                .status("DISABLED")
                .message("No hay job en ejecucion en esta instancia.")
                .requested(0)
                .queued(0)
                .build()));
    }
}
