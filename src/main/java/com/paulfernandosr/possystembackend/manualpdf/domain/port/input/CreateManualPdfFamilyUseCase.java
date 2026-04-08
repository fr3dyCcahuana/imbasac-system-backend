package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamilyUpsertCommand;

public interface CreateManualPdfFamilyUseCase {
    ManualPdfFamily create(ManualPdfFamilyUpsertCommand command);
}
