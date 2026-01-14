package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.salev2.domain.model.VoidSaleV2Response;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.VoidSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.VoidSaleV2Request;
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
    private final VoidSaleV2UseCase voidSaleV2UseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<SaleV2DocumentResponse>> create(@RequestBody SaleV2CreateRequest request,
                                                                          Principal principal) {
        SaleV2DocumentResponse document = createSaleV2UseCase.create(request, principal.getName());
        return ResponseEntity
                .created(URI.create("/sales/v2/" + document.getSaleId()))
                .body(SuccessResponse.ok(document));
    }

    /**
     * Anulación de venta con reversa de stock/kardex/seriales y ajuste de CxC.
     * Regla clave: si es serial, el status queda en DEVUELTO.
     */
    @PostMapping("/{saleId}/void")
    public ResponseEntity<SuccessResponse<VoidSaleV2Response>> voidSale(@PathVariable Long saleId,
                                                                        @RequestBody(required = false) VoidSaleV2Request request) {
        String reason = request == null ? null : request.getReason();
        VoidSaleV2Response response = voidSaleV2UseCase.voidSale(saleId, reason);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
