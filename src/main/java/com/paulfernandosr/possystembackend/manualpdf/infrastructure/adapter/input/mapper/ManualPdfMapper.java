package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.mapper;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocumentDetail;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfImage;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.ManualPdfPublicUrlService;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfDocumentResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfFamilyResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfImageResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfModelResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ManualPdfMapper {

    private final ManualPdfPublicUrlService urlService;

    public ManualPdfFamilyResponse family(ManualPdfFamily family) {
        return new ManualPdfFamilyResponse(family.id(), family.code(), family.name(), family.sortOrder());
    }

    public ManualPdfModelResponse model(ManualPdfModel model) {
        return new ManualPdfModelResponse(model.id(), model.familyId(), model.code(), model.name(), model.sortOrder());
    }

    public ManualPdfImageResponse image(ManualPdfImage image) {
        return new ManualPdfImageResponse(image.id(), image.fileName(), urlService.imageUrl(image.fileKey()), image.sortOrder());
    }

    public ManualPdfDocumentResponse documentDetail(ManualPdfDocumentDetail detail) {
        return document(detail.document(), detail.images());
    }

    public ManualPdfDocumentResponse document(ManualPdfDocument document, List<ManualPdfImage> images) {
        return new ManualPdfDocumentResponse(
                document.id(),
                document.modelId(),
                document.title(),
                document.fileName(),
                document.yearFrom(),
                document.yearTo(),
                urlService.pdfPreviewUrl(document.fileKey()),
                urlService.pdfDownloadUrl(document.fileKey()),
                images.stream().map(this::image).toList()
        );
    }
}