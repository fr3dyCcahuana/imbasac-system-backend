package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.SerialUnitContractRow;

public interface ContractSerialUnitRepository {

    SerialUnitContractRow lockById(Long serialUnitId);

    void reserveForContract(Long serialUnitId, Long contractId);

    void releaseFromContract(Long contractId);

    void assertStillReserved(Long contractId, Long serialUnitId);
}
