package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfDocumentNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.PreviewManualPdfUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfFileRepository;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreviewManualPdfService implements PreviewManualPdfUseCase {

    private final ManualPdfRepository repository;
    private final ManualPdfFileRepository fileRepository;

    @Override
    public ManualPdfFile preview(Long documentId) {
        ManualPdfDocument document = repository.findById(documentId)
                .orElseThrow(() -> new ManualPdfDocumentNotFoundException(
                        "No se encontró el documento PDF."
                ));

        return fileRepository.load(
                document.fileKey(),
                document.fileName(),
                document.mimeType()
        );
    }
}
