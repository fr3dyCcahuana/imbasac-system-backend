package com.paulfernandosr.possystembackend.manualpdf.domain.port.output;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelStorageContext;

import java.util.List;
import java.util.Optional;

public interface ManualPdfRepository {

    List<Integer> findAvailableYears();

    List<ManualPdfFamily> findFamiliesByYear(int year);

    List<ManualPdfModel> findModelsByYearAndFamily(int year, Long familyId);

    Optional<ManualPdfDocument> findBestDocumentByYearAndModel(int year, Long modelId);

    Optional<ManualPdfDocument> findById(Long id);

    Optional<ManualPdfModelStorageContext> findModelStorageContextById(Long modelId);

    boolean existsOverlappingDocument(Long modelId, Integer yearFrom, Integer yearTo);

    boolean familyExists(Long familyId);

    ManualPdfFamily upsertFamily(String code, String name, Integer sortOrder, Boolean enabled);

    ManualPdfModel upsertModel(Long familyId, String code, String name, Integer sortOrder, Boolean enabled);

    ManualPdfDocument createDocument(ManualPdfDocument document);
}
