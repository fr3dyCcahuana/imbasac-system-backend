package com.paulfernandosr.possystembackend.manualpdf.domain.port.input;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfRegistrationCommand;
import org.springframework.web.multipart.MultipartFile;

public interface CreateManualPdfUseCase {
    ManualPdfDocument create(ManualPdfRegistrationCommand command, MultipartFile file);
}
