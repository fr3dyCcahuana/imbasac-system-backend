package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;

import java.util.List;

public interface GetManualPdfModelsUseCase {
    List<ManualPdfModel> getModels(int year, Long familyId);
}
