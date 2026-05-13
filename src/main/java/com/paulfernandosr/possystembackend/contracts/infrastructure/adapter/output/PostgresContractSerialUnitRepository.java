package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractSerialUnitRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.SerialUnitContractRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractSerialUnitRepository implements ContractSerialUnitRepository {

    private final JdbcClient jdbcClient;

    @Override
    public SerialUnitContractRow lockById(Long serialUnitId) {
        String sql = """
            SELECT id,
                   product_id AS productId,
                   status,
                   vin,
                   contract_id AS contractId
              FROM product_serial_unit
             WHERE id = ?
             FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .param(serialUnitId)
                .query(SerialUnitContractRow.class)
                .optional()
                .orElse(null);
    }

    @Override
    public void reserveForContract(Long serialUnitId, Long contractId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'RESERVADO',
                   contract_id = ?,
                   updated_at = NOW()
             WHERE id = ?
               AND contract_id IS NULL
               AND status = 'EN_ALMACEN'
        """;

        int updated = jdbcClient.sql(sql)
                .params(contractId, serialUnitId)
                .update();

        if (updated == 0) {
            throw new InvalidContractException("No se pudo reservar la unidad serial. Puede estar reservada o no disponible.");
        }
    }

    @Override
    public void markDeliveredOnCredit(Long contractId, Long serialUnitId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'ENTREGADO_CREDITO',
                   updated_at = NOW()
             WHERE id = ?
               AND contract_id = ?
               AND status = 'RESERVADO'
        """;

        int updated = jdbcClient.sql(sql)
                .params(serialUnitId, contractId)
                .update();

        if (updated == 0) {
            throw new InvalidContractException("No se pudo marcar la unidad como ENTREGADO_CREDITO. Debe estar RESERVADO por este contrato.");
        }
    }

    @Override
    public void markRecoveredForReview(Long contractId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'RECUPERADO_REVISION',
                   sale_item_id = NULL,
                   updated_at = NOW()
             WHERE contract_id = ?
               AND status IN ('RESERVADO','ENTREGADO_CREDITO')
        """;

        int updated = jdbcClient.sql(sql)
                .param(contractId)
                .update();

        if (updated == 0) {
            throw new InvalidContractException("No se pudo marcar la unidad como RECUPERADO_REVISION. Verifique el estado de la moto.");
        }
    }

    @Override
    public void releaseRecoveredToStock(Long contractId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'EN_ALMACEN',
                   contract_id = NULL,
                   sale_item_id = NULL,
                   updated_at = NOW()
             WHERE contract_id = ?
               AND status = 'RECUPERADO_REVISION'
        """;

        int updated = jdbcClient.sql(sql)
                .param(contractId)
                .update();

        if (updated == 0) {
            throw new InvalidContractException("No se encontró una unidad RECUPERADO_REVISION para liberar a almacén.");
        }
    }

    @Override
    public void releaseFromContract(Long contractId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'EN_ALMACEN',
                   contract_id = NULL,
                   updated_at = NOW()
             WHERE contract_id = ?
               AND status = 'RESERVADO'
        """;
        jdbcClient.sql(sql)
                .param(contractId)
                .update();
    }

    @Override
    public void releaseSpecificFromContract(Long contractId, Long serialUnitId) {
        String sql = """
            UPDATE product_serial_unit
               SET status = 'EN_ALMACEN',
                   contract_id = NULL,
                   updated_at = NOW()
             WHERE id = ?
               AND contract_id = ?
               AND status = 'RESERVADO'
        """;

        jdbcClient.sql(sql)
                .params(serialUnitId, contractId)
                .update();
    }

    @Override
    public void assertStillReserved(Long contractId, Long serialUnitId) {
        assertStatus(contractId, serialUnitId, "RESERVADO");
    }

    @Override
    public void assertStillReservedOrDelivered(Long contractId, Long serialUnitId) {
        String sql = """
            SELECT COUNT(*)
              FROM product_serial_unit
             WHERE id = ?
               AND contract_id = ?
               AND status IN ('RESERVADO','ENTREGADO_CREDITO')
        """;

        Long count = jdbcClient.sql(sql)
                .params(serialUnitId, contractId)
                .query(Long.class)
                .single();

        if (count == null || count == 0) {
            throw new InvalidContractException("La unidad serial ya no está reservada/entregada por este contrato.");
        }
    }

    private void assertStatus(Long contractId, Long serialUnitId, String status) {
        String sql = """
            SELECT COUNT(*)
              FROM product_serial_unit
             WHERE id = ?
               AND contract_id = ?
               AND status = ?
        """;

        Long count = jdbcClient.sql(sql)
                .params(serialUnitId, contractId, status)
                .query(Long.class)
                .single();

        if (count == null || count == 0) {
            throw new InvalidContractException("La unidad serial no está en estado " + status + " para este contrato.");
        }
    }
}
