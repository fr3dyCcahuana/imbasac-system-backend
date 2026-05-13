package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.SerialUnitContractRow;

public interface ContractSerialUnitRepository {

    SerialUnitContractRow lockById(Long serialUnitId);

    void reserveForContract(Long serialUnitId, Long contractId);

    void markDeliveredOnCredit(Long contractId, Long serialUnitId);

    void markRecoveredForReview(Long contractId);

    void releaseRecoveredToStock(Long contractId);

    void releaseFromContract(Long contractId);

    void releaseSpecificFromContract(Long contractId, Long serialUnitId);

    void assertStillReserved(Long contractId, Long serialUnitId);

    void assertStillReservedOrDelivered(Long contractId, Long serialUnitId);
}
