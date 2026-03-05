package com.paulfernandosr.possystembackend.contracts.domain.port.output;

public interface SaleContractLinkRepository {
    void linkSaleToContract(Long saleId, Long contractId);
}
