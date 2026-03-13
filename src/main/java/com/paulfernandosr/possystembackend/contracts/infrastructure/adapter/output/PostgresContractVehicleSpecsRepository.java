package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractVehicleSpecsRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.VehicleSpecsRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractVehicleSpecsRepository implements ContractVehicleSpecsRepository {

    private final JdbcClient jdbcClient;

    @Override
    public VehicleSpecsRow findByProductId(Long productId) {

        String sql = """
            SELECT
              product_id AS productId,
              vehicle_type AS vehicleType,
              bodywork,
              engine_capacity AS engineCapacity,
              fuel,
              cylinders,
              net_weight AS netWeight,
              payload,
              gross_weight AS grossWeight,
              vehicle_class AS vehicleClass,
              engine_power AS enginePower,
              rolling_form AS rollingForm,
              seats,
              passengers,
              axles,
              wheels,
              length,
              width,
              height
            FROM product_vehicle_specs
            WHERE product_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(VehicleSpecsRow.class)
                .optional()
                .orElse(null);
    }
}
