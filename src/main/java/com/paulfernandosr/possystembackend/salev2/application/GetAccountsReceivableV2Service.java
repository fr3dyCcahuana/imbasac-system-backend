package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidReceivableException;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetAccountsReceivableV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivableQueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetAccountsReceivableV2Service implements GetAccountsReceivableV2UseCase {

    private final AccountsReceivableQueryRepository queryRepository;

    @Override
    public AccountsReceivableDetailResponse getById(Long arId) {
        AccountsReceivableDetailResponse resp = queryRepository.findById(arId);
        if (resp == null) {
            throw new InvalidReceivableException("Cuenta por cobrar no encontrada: " + arId);
        }
        return resp;
    }
}
