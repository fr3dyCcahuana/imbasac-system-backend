package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableSummaryResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;

public interface GetAccountsReceivablePageV2UseCase {
    PageResponse<AccountsReceivableSummaryResponse> findPage(Long customerId, String status, String query, int page, int size);
}
