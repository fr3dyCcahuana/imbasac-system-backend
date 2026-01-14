package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableDetailResponse;

public interface GetAccountsReceivableV2UseCase {
    AccountsReceivableDetailResponse getById(Long arId);
}
