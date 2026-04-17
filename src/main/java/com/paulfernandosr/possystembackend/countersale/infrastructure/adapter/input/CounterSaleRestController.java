package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.countersale.domain.model.VoidCounterSaleResponse;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.CreateCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.EmitCounterSaleSunatCombinationUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSalePageUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetElectronicReceiptPrintableUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.ValidateCounterSaleSunatCombinationUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.VoidCounterSaleUseCase;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/counter-sales")
@RequiredArgsConstructor
public class CounterSaleRestController {

    private final CreateCounterSaleUseCase createCounterSaleUseCase;
    private final GetCounterSalePageUseCase getCounterSalePageUseCase;
    private final GetCounterSaleUseCase getCounterSaleUseCase;
    private final GetElectronicReceiptPrintableUseCase getElectronicReceiptPrintableUseCase;
    private final VoidCounterSaleUseCase voidCounterSaleUseCase;
    private final ValidateCounterSaleSunatCombinationUseCase validateCounterSaleSunatCombinationUseCase;
    private final EmitCounterSaleSunatCombinationUseCase emitCounterSaleSunatCombinationUseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<CounterSaleDocumentResponse>> create(@RequestBody CounterSaleCreateRequest request,
                                                                               Principal principal) {
        CounterSaleDocumentResponse document = createCounterSaleUseCase.create(request, principal.getName());
        return ResponseEntity
                .created(URI.create("/counter-sales/" + document.getCounterSaleId()))
                .body(SuccessResponse.ok(document));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<CounterSaleSummaryResponse>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) Long number,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                getCounterSalePageUseCase.findPage(query, series, number, status, page, size)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<CounterSaleDetailResponse>> getById(@PathVariable("id") Long counterSaleId) {
        return ResponseEntity.ok(SuccessResponse.ok(getCounterSaleUseCase.getById(counterSaleId)));
    }

    @GetMapping("/sunat-combinations/{comboId}/printable")
    public ResponseEntity<SuccessResponse<ElectronicReceiptPrintableResponse>> getSunatCombinationPrintable(@PathVariable Long comboId) {
        return ResponseEntity.ok(SuccessResponse.ok(getElectronicReceiptPrintableUseCase.getByComboId(comboId)));
    }

    @PostMapping("/{counterSaleId}/void")
    public ResponseEntity<SuccessResponse<VoidCounterSaleResponse>> voidSale(@PathVariable Long counterSaleId,
                                                                             @RequestBody(required = false) VoidCounterSaleRequest request,
                                                                             Principal principal) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(
                voidCounterSaleUseCase.voidSale(counterSaleId, reason, principal.getName())
        ));
    }

    @PostMapping("/{counterSaleId}/sunat-combination/validate")
    public ResponseEntity<SuccessResponse<CounterSaleSunatCombinationValidationResponse>> validateSunatCombination(
            @PathVariable Long counterSaleId,
            @RequestBody(required = false) CounterSaleSunatCombinationRequest request) {
        return ResponseEntity.ok(SuccessResponse.ok(
                validateCounterSaleSunatCombinationUseCase.validate(counterSaleId, request)
        ));
    }

    @PostMapping("/{counterSaleId}/sunat-combination/emit")
    public ResponseEntity<SuccessResponse<CounterSaleSunatCombinationEmitResponse>> emitSunatCombination(
            @PathVariable Long counterSaleId,
            @RequestBody(required = false) CounterSaleSunatCombinationRequest request,
            Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                emitCounterSaleSunatCombinationUseCase.emit(counterSaleId, request, principal.getName())
        ));
    }

}
