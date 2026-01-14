package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.model.VoidProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.VoidProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoidProformaV2Service implements VoidProformaV2UseCase {

    private final ProformaRepository proformaRepository;

    @Override
    @Transactional
    public VoidProformaV2Response voidProforma(Long proformaId, String reason) {
        if (proformaId == null) {
            throw new InvalidProformaV2Exception("proformaId es requerido");
        }

        var lockedOpt = proformaRepository.lockById(proformaId);
        if (lockedOpt.isEmpty()) {
            throw new InvalidProformaV2Exception("Proforma no encontrada: " + proformaId);
        }

        var locked = lockedOpt.get();
        if (!ProformaStatus.PENDIENTE.equals(locked.getStatus())) {
            throw new InvalidProformaV2Exception("Solo se puede anular una proforma PENDIENTE. Estado actual: " + locked.getStatus());
        }

        // No existe columna void_reason; se deja trazabilidad en notes.
        String voidNote = (reason == null || reason.isBlank()) ? "ANULADA" : "ANULADA: " + reason;
        proformaRepository.appendNotesAndSetStatus(proformaId, voidNote, "ANULADA");

        return VoidProformaV2Response.builder()
                .proformaId(proformaId)
                .status("ANULADA")
                .build();
    }
}
