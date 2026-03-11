package com.paulfernandosr.possystembackend.contracts.domain.port.input;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractInstallmentPaymentRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractInstallmentPaymentResponse;

public interface PayContractInstallmentUseCase {
    ContractInstallmentPaymentResponse pay(Long contractId, int installmentNumber, ContractInstallmentPaymentRequest request, String username);
}
