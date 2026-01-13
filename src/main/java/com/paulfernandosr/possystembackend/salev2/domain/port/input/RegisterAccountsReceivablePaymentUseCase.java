package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentResponse;

public interface RegisterAccountsReceivablePaymentUseCase {
    AccountsReceivablePaymentResponse register(Long arId, AccountsReceivablePaymentRequest request);
}
