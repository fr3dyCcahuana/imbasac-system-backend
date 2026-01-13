package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/v2")
public class SaleV2RestController {

    private final CreateSaleV2UseCase createSaleV2UseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<SaleV2DocumentResponse>> create(@RequestBody SaleV2CreateRequest request,
                                                                          Principal principal) {
        SaleV2DocumentResponse document = createSaleV2UseCase.create(request, principal.getName());
        return ResponseEntity
                .created(URI.create("/sales/" + document.getSaleId()))
                .body(SuccessResponse.ok(document));
    }
}
