package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableDetailResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableSummaryResponse;

import java.util.List;

public interface AccountsReceivableQueryRepository {

    long countPage(Long customerId, String status, String likeParam);

    List<AccountsReceivableSummaryResponse> findPage(Long customerId, String status, String likeParam, int limit, int offset);

    AccountsReceivableDetailResponse findById(Long arId);
}
