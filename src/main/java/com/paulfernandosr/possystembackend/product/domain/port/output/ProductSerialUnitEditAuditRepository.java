package com.paulfernandosr.possystembackend.product.domain.port.output;

public interface ProductSerialUnitEditAuditRepository {

    void insert(Long serialUnitId,
                Long editedBy,
                String editedByUsername,
                String reason,
                String beforeJson,
                String afterJson);
}
