package com.paulfernandosr.possystembackend.manualpdf.domain;

import java.util.List;

public record ManualPdfDocumentDetail(
        ManualPdfDocument document,
        List<ManualPdfImage> images
) {
}
