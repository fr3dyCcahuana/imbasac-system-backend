package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;

import java.util.Optional;

public interface ProformaRepository {
    Proforma create(Proforma proforma);
    Optional<Proforma> lockById(Long proformaId); // FOR UPDATE
    Optional<Proforma> findById(Long proformaId);
    void updateStatus(Long proformaId, String status);
    void touchUpdatedAt(Long proformaId);
}
