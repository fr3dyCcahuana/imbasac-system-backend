package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;

import java.util.Optional;

public interface ProformaRepository {
    Proforma create(Proforma proforma);
    Optional<Proforma> lockById(Long proformaId); // FOR UPDATE por ID interno
    Optional<Proforma> lockByNumber(Long number); // FOR UPDATE por número visible de proforma
    Optional<Proforma> findById(Long proformaId);
    Optional<Proforma> findByNumber(Long number);
    void updateStatus(Long proformaId, String status);
    void appendNotesAndSetStatus(Long proformaId, String noteToAppend, String status);
    void updateEditable(Proforma proforma);
    void touchUpdatedAt(Long proformaId);
    int markAsConverted(Long proformaId, Long saleId, Long convertedBy);
}
