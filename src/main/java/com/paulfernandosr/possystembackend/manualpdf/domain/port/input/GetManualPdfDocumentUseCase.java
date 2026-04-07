package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;

public interface GetManualPdfDocumentUseCase {
    ManualPdfDocument getDocument(int year, Long modelId);
}
