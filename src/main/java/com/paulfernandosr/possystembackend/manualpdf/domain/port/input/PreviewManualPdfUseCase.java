package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;

public interface PreviewManualPdfUseCase {
    ManualPdfFile preview(Long documentId);
}
