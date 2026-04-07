package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;

import java.util.List;

public interface GetManualPdfFamiliesUseCase {
    List<ManualPdfFamily> getFamilies(int year);
}
