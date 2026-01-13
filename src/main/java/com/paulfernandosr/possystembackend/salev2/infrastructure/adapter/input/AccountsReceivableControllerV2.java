package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.salev2.domain.port.input.RegisterAccountsReceivablePaymentUseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts-receivable")
@RequiredArgsConstructor
public class AccountsReceivableControllerV2 {

    private final RegisterAccountsReceivablePaymentUseCase registerAccountsReceivablePaymentUseCase;

    @PostMapping("/{arId}/payments")
    public ResponseEntity<AccountsReceivablePaymentResponse> registerPayment(
            @PathVariable Long arId,
            @RequestBody AccountsReceivablePaymentRequest request
    ) {
        return ResponseEntity.ok(registerAccountsReceivablePaymentUseCase.register(arId, request));
    }
}
