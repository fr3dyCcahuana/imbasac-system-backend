package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocumentDetail;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfImage;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelSummary;
import com.paulfernandosr.possystembackend.manualpdf.domain.StoredFileResult;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfBadRequestException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfConflictException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.LocalManualPdfStorage;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.output.ManualPdfRepository;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.util.ManualPdfPathUtils;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.util.ManualPdfTextUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ManualPdfService {

    private static final int MAX_IMAGES = 10;

    private final ManualPdfRepository repository;
    private final LocalManualPdfStorage storage;

    public ManualPdfFamily createFamily(String code, String name, Integer sortOrder) {
        String safeName = ManualPdfTextUtils.requireTrimmed(name, "name");
        String safeCode = ManualPdfTextUtils.normalizeFamilyCode(code, safeName);
        int safeSort = sortOrder == null ? 0 : sortOrder;

        repository.findFamilyByCode(safeCode).ifPresent(existing -> {
            throw new ManualPdfConflictException("Ya existe una familia con el mismo código.");
        });

        return repository.insertFamily(safeCode, safeName, safeSort);
    }

    public ManualPdfModel createModel(Long familyId, String code, String name, Integer sortOrder) {
        if (familyId == null) {
            throw new IllegalArgumentException("familyId es obligatorio.");
        }
        repository.findFamilyById(familyId)
                .orElseThrow(() -> new ManualPdfNotFoundException("No se encontró la familia seleccionada."));

        String safeName = ManualPdfTextUtils.requireTrimmed(name, "name");
        String safeCode = ManualPdfTextUtils.normalizeModelCode(code, safeName);
        int safeSort = sortOrder == null ? 0 : sortOrder;

        repository.findModelByFamilyAndCode(familyId, safeCode).ifPresent(existing -> {
            throw new ManualPdfConflictException("Ya existe un modelo con el mismo código en la familia seleccionada.");
        });

        return repository.insertModel(
                familyId,
                safeCode,
                safeName,
                ManualPdfTextUtils.normalizeName(safeName),
                safeSort
        );
    }

    public List<Integer> getYears() {
        return repository.findAvailableYears();
    }

    public List<ManualPdfFamily> getFamilies(int year) {
        return repository.findFamiliesByYear(year);
    }

    public List<ManualPdfModel> getModels(int year, Long familyId) {
        return repository.findModelsByYearAndFamily(year, familyId);
    }

    public List<ManualPdfDocumentDetail> getModelDetails(int year, Long familyId) {
        return repository.findModelsByYearAndFamily(year, familyId).stream()
                .map(model -> repository.findBestDocumentByYearAndModel(year, model.id())
                        .map(document -> new ManualPdfDocumentDetail(document, repository.findImagesByDocumentId(document.id())))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public ManualPdfDocument getDocumentByFilter(int year, Long modelId) {
        return repository.findBestDocumentByYearAndModel(year, modelId)
                .orElseThrow(() -> new ManualPdfNotFoundException("No se encontró PDF para el año y vehículo seleccionado."));
    }

    public ManualPdfDocument getDocumentById(Long documentId) {
        return repository.findDocumentById(documentId)
                .orElseThrow(() -> new ManualPdfNotFoundException("No se encontró el documento PDF."));
    }

    public List<ManualPdfImage> getImages(Long documentId) {
        return repository.findImagesByDocumentId(documentId);
    }

    public ManualPdfDocument createDocument(
            Long modelId,
            String title,
            Integer yearFrom,
            Integer yearTo,
            MultipartFile file,
            List<MultipartFile> images
    ) {
        validateCreateOrUpdate(modelId, title, yearFrom, yearTo, file, images, false);

        if (repository.existsDocumentByModelAndRange(modelId, yearFrom, yearTo)) {
            throw new ManualPdfConflictException("Ya existe un PDF registrado para el mismo modelo y rango de años.");
        }

        ManualPdfModelSummary summary = repository.getModelSummary(modelId);
        Long documentId = repository.insertDocumentPlaceholder(modelId, title.trim(), yearFrom, yearTo);

        String pdfName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document_" + documentId + ".pdf";
        StoredFileResult storedPdf = storage.save(
                file,
                ManualPdfPathUtils.documentDir(summary, documentId),
                ManualPdfTextUtils.sanitizeFileName(pdfName, "document_" + documentId + ".pdf")
        );

        repository.updateDocumentStoredFile(
                documentId,
                storedPdf.fileName(),
                storedPdf.fileKey(),
                "application/pdf",
                storedPdf.fileSize()
        );

        if (images != null && !images.isEmpty()) {
            int sortOrder = 1;
            for (MultipartFile image : images) {
                String original = image.getOriginalFilename() != null ? image.getOriginalFilename() : "image_" + sortOrder;
                String preferred = String.format("%02d_%s", sortOrder, ManualPdfTextUtils.sanitizeFileName(original, "image_" + sortOrder));
                StoredFileResult storedImage = storage.save(
                        image,
                        ManualPdfPathUtils.imagesDir(summary, documentId),
                        preferred
                );
                repository.insertImage(
                        documentId,
                        storedImage.fileName(),
                        storedImage.fileKey(),
                        storedImage.mimeType(),
                        storedImage.fileSize(),
                        sortOrder
                );
                sortOrder++;
            }
        }

        return getDocumentById(documentId);
    }

    public ManualPdfDocument updateDocument(
            Long documentId,
            Long modelId,
            String title,
            Integer yearFrom,
            Integer yearTo,
            MultipartFile file,
            List<MultipartFile> newImages,
            List<Long> removeImageIds
    ) {
        ManualPdfDocument current = getDocumentById(documentId);
        List<ManualPdfImage> currentImages = repository.findImagesByDocumentId(documentId);

        Long targetModelId = modelId != null ? modelId : current.modelId();
        String targetTitle = title != null && !title.isBlank() ? title.trim() : current.title();
        Integer targetYearFrom = yearFrom != null ? yearFrom : current.yearFrom();
        Integer targetYearTo = yearTo != null ? yearTo : current.yearTo();

        validateCreateOrUpdate(targetModelId, targetTitle, targetYearFrom, targetYearTo, file, newImages, true);

        if (repository.existsAnotherDocumentByModelAndRange(documentId, targetModelId, targetYearFrom, targetYearTo)) {
            throw new ManualPdfConflictException("Ya existe un PDF registrado para el mismo modelo y rango de años.");
        }

        ManualPdfModelSummary currentSummary = repository.getModelSummary(current.modelId());
        ManualPdfModelSummary targetSummary = repository.getModelSummary(targetModelId);

        Set<Long> removeSet = new HashSet<>(removeImageIds == null ? List.of() : removeImageIds);
        List<ManualPdfImage> imagesToRemove = repository.findImagesByIds(documentId, removeImageIds == null ? List.of() : removeImageIds);
        if (removeSet.size() != imagesToRemove.size()) {
            throw new ManualPdfBadRequestException("Una o más imágenes a eliminar no pertenecen al documento seleccionado.");
        }

        int remaining = currentImages.size() - imagesToRemove.size();
        int incoming = newImages == null ? 0 : newImages.size();
        if (remaining + incoming > MAX_IMAGES) {
            throw new ManualPdfBadRequestException("Solo se permiten hasta " + MAX_IMAGES + " imágenes por registro.");
        }

        for (ManualPdfImage image : imagesToRemove) {
            storage.delete(image.fileKey());
        }
        repository.deleteImagesByIds(documentId, removeImageIds);

        boolean modelChanged = !current.modelId().equals(targetModelId);

        if (modelChanged && (file == null || file.isEmpty())) {
            String newPdfKey = ManualPdfPathUtils.documentFileKey(targetSummary, documentId, current.fileName());
            storage.move(current.fileKey(), newPdfKey);
            repository.updateDocumentStoredFile(documentId, current.fileName(), newPdfKey, current.mimeType(), current.fileSize());
            current = getDocumentById(documentId);
        }

        if (modelChanged) {
            List<ManualPdfImage> imagesToKeep = repository.findImagesByDocumentId(documentId);
            for (ManualPdfImage image : imagesToKeep) {
                String newKey = ManualPdfPathUtils.imageFileKey(targetSummary, documentId, image.fileName());
                storage.move(image.fileKey(), newKey);
                repository.updateImageFileKey(image.id(), newKey);
            }
        }

        if (file != null && !file.isEmpty()) {
            String oldKey = current.fileKey();
            String pdfName = file.getOriginalFilename() != null ? file.getOriginalFilename() : current.fileName();
            StoredFileResult storedPdf = storage.save(
                    file,
                    ManualPdfPathUtils.documentDir(targetSummary, documentId),
                    ManualPdfTextUtils.sanitizeFileName(pdfName, "document_" + documentId + ".pdf")
            );
            repository.updateDocumentStoredFile(documentId, storedPdf.fileName(), storedPdf.fileKey(), "application/pdf", storedPdf.fileSize());
            if (!oldKey.equals(storedPdf.fileKey())) {
                storage.delete(oldKey);
            }
        }

        if (newImages != null && !newImages.isEmpty()) {
            int nextOrder = repository.findNextImageSortOrder(documentId);
            for (MultipartFile image : newImages) {
                String original = image.getOriginalFilename() != null ? image.getOriginalFilename() : "image_" + nextOrder;
                String preferred = String.format("%02d_%s", nextOrder, ManualPdfTextUtils.sanitizeFileName(original, "image_" + nextOrder));
                StoredFileResult storedImage = storage.save(
                        image,
                        ManualPdfPathUtils.imagesDir(targetSummary, documentId),
                        preferred
                );
                repository.insertImage(
                        documentId,
                        storedImage.fileName(),
                        storedImage.fileKey(),
                        storedImage.mimeType(),
                        storedImage.fileSize(),
                        nextOrder
                );
                nextOrder++;
            }
        }

        repository.updateDocumentMetadata(documentId, targetModelId, targetTitle, targetYearFrom, targetYearTo);
        return getDocumentById(documentId);
    }

    public ManualPdfFile loadPdf(Long documentId) {
        ManualPdfDocument document = getDocumentById(documentId);
        return storage.load(document.fileKey(), document.mimeType());
    }

    public ManualPdfFile loadImage(Long documentId, Long imageId) {
        ManualPdfImage image = repository.findImageById(imageId)
                .orElseThrow(() -> new ManualPdfNotFoundException("No se encontró la imagen seleccionada."));
        if (!image.documentId().equals(documentId)) {
            throw new ManualPdfNotFoundException("La imagen no pertenece al documento solicitado.");
        }
        return storage.load(image.fileKey(), image.mimeType());
    }

    private void validateCreateOrUpdate(
            Long modelId,
            String title,
            Integer yearFrom,
            Integer yearTo,
            MultipartFile pdfFile,
            List<MultipartFile> images,
            boolean update
    ) {
        if (modelId == null) {
            throw new IllegalArgumentException("modelId es obligatorio.");
        }
        repository.findModelById(modelId)
                .orElseThrow(() -> new ManualPdfNotFoundException("No se encontró el modelo seleccionado."));

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title es obligatorio.");
        }
        if (yearFrom == null) {
            throw new IllegalArgumentException("yearFrom es obligatorio.");
        }
        if (yearTo == null) {
            throw new IllegalArgumentException("yearTo es obligatorio.");
        }
        if (yearFrom > yearTo) {
            throw new ManualPdfBadRequestException("yearFrom no puede ser mayor que yearTo.");
        }

        if (!update) {
            validatePdf(pdfFile);
        } else if (pdfFile != null && !pdfFile.isEmpty()) {
            validatePdf(pdfFile);
        }

        if (images != null && !images.isEmpty()) {
            if (images.size() > MAX_IMAGES) {
                throw new ManualPdfBadRequestException("Solo se permiten hasta " + MAX_IMAGES + " imágenes por registro.");
            }
            for (MultipartFile image : images) {
                validateImage(image);
            }
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ManualPdfBadRequestException("El archivo PDF es obligatorio.");
        }
        String contentType = file.getContentType();
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean validMime = "application/pdf".equals(contentType);
        boolean validExt = original.endsWith(".pdf");
        if (!validMime && !validExt) {
            throw new ManualPdfBadRequestException("Solo se permiten archivos PDF.");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ManualPdfBadRequestException("Se recibió una imagen vacía.");
        }
        String contentType = image.getContentType();
        String original = image.getOriginalFilename() == null ? "" : image.getOriginalFilename().toLowerCase();
        boolean validMime = "image/jpeg".equals(contentType)
                || "image/jpg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
        boolean validExt = original.endsWith(".jpg")
                || original.endsWith(".jpeg")
                || original.endsWith(".png")
                || original.endsWith(".webp");
        if (!validMime && !validExt) {
            throw new ManualPdfBadRequestException("Solo se permiten imágenes jpg, jpeg, png o webp.");
        }
    }
}