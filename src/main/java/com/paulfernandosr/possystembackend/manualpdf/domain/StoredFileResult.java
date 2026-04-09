package com.paulfernandosr.possystembackend.manualpdf.domain;

public record StoredFileResult(
        String fileName,
        String fileKey,
        String mimeType,
        long fileSize
) {
}
