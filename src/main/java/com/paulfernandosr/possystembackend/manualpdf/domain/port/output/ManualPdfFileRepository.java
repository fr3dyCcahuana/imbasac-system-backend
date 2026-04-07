package com.paulfernandosr.possystembackend.manualpdf.domain.port.output;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfStoredFile;
import org.springframework.web.multipart.MultipartFile;

public interface ManualPdfFileRepository {

    ManualPdfStoredFile store(String familyCode, String modelCode, Integer yearFrom, Integer yearTo, MultipartFile file);

    ManualPdfFile load(String fileKey, String fileName, String mimeType);

    void deleteByKey(String fileKey);
}
