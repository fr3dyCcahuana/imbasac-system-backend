package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.VehicleSpecsRow;

public interface ContractVehicleSpecsRepository {
    VehicleSpecsRow findByProductId(Long productId);
}
