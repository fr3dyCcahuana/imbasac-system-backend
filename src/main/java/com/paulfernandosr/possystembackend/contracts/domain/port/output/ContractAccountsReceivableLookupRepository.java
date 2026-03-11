package com.paulfernandosr.possystembackend.contracts.domain.port.output;

public interface ContractAccountsReceivableLookupRepository {
    Long findArIdBySaleId(Long saleId);
}
