package com.paulfernandosr.possystembackend.product.domain.port.input;

public interface ExportProductReferenceInfoUseCase {
    byte[] exportFromSkuWorkbook(byte[] fileBytes, String originalFilename);
}
