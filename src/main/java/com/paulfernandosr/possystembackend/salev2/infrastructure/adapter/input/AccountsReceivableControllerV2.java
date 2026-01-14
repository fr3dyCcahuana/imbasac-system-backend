package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetAccountsReceivablePageV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetAccountsReceivableV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.RegisterAccountsReceivablePaymentUseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts-receivable")
@RequiredArgsConstructor
public class AccountsReceivableControllerV2 {

    private final RegisterAccountsReceivablePaymentUseCase registerAccountsReceivablePaymentUseCase;
    private final GetAccountsReceivablePageV2UseCase getAccountsReceivablePageV2UseCase;
    private final GetAccountsReceivableV2UseCase getAccountsReceivableV2UseCase;

    @PostMapping("/{arId}/payments")
    public ResponseEntity<SuccessResponse<AccountsReceivablePaymentResponse>> registerPayment(
            @PathVariable Long arId,
            @RequestBody AccountsReceivablePaymentRequest request
    ) {
        AccountsReceivablePaymentResponse response = registerAccountsReceivablePaymentUseCase.register(arId, request);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    /**
     * Listado paginado de CxC (nuevo).
     *
     * customerId: filtra por cliente (opcional)
     * status: PENDIENTE / PAGADO / VENCIDO (opcional)
     * query: busca por cliente (nombre/doc) o "DOC SERIE-NUM" (opcional)
     */
    @GetMapping("/v2")
    public ResponseEntity<SuccessResponse<PageResponse<AccountsReceivableSummaryResponse>>> findPage(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                getAccountsReceivablePageV2UseCase.findPage(customerId, status, query, page, size)
        ));
    }

    /**
     * Detalle de CxC con su lista de abonos (nuevo).
     */
    @GetMapping("/v2/{arId}")
    public ResponseEntity<SuccessResponse<AccountsReceivableDetailResponse>> getById(@PathVariable Long arId) {
        return ResponseEntity.ok(SuccessResponse.ok(getAccountsReceivableV2UseCase.getById(arId)));
    }
}
