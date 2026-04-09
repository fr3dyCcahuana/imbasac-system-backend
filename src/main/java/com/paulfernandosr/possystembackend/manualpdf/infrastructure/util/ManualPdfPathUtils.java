package com.paulfernandosr.possystembackend.manualpdf.infrastructure.util;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelSummary;

public final class ManualPdfPathUtils {

    private ManualPdfPathUtils() {
    }

    public static String documentDir(ManualPdfModelSummary summary, Long documentId) {
        return summary.familyCode() + "/" + summary.modelCode() + "/" + documentId + "/document";
    }

    public static String imagesDir(ManualPdfModelSummary summary, Long documentId) {
        return summary.familyCode() + "/" + summary.modelCode() + "/" + documentId + "/images";
    }

    public static String documentFileKey(ManualPdfModelSummary summary, Long documentId, String fileName) {
        return documentDir(summary, documentId) + "/" + fileName;
    }

    public static String imageFileKey(ManualPdfModelSummary summary, Long documentId, String fileName) {
        return imagesDir(summary, documentId) + "/" + fileName;
    }
}
