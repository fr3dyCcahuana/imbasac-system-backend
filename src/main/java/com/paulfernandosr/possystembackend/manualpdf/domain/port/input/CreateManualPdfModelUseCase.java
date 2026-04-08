package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelUpsertCommand;

public interface CreateManualPdfModelUseCase {
    ManualPdfModel create(ManualPdfModelUpsertCommand command);
}
