package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.manualpdf.application.ManualPdfService;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocumentDetail;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfImage;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.CreateManualPdfFamilyRequest;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.CreateManualPdfModelRequest;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfDocumentResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfFamilyResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfImageResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfModelResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.SuccessResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.mapper.ManualPdfMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManualPdfController {

    private final ManualPdfService service;
    private final ManualPdfMapper mapper;

    @PostMapping("/manual-pdf-families")
    public ResponseEntity<SuccessResponse<ManualPdfFamilyResponse>> createFamily(
            @RequestBody CreateManualPdfFamilyRequest request
    ) {
        ManualPdfFamily family = service.createFamily(request.code(), request.name(), request.sortOrder());
        return ResponseEntity.status(201).body(SuccessResponse.created(mapper.family(family)));
    }

    @PostMapping("/manual-pdf-models")
    public ResponseEntity<SuccessResponse<ManualPdfModelResponse>> createModel(
            @RequestBody CreateManualPdfModelRequest request
    ) {
        ManualPdfModel model = service.createModel(request.familyId(), request.code(), request.name(), request.sortOrder());
        return ResponseEntity.status(201).body(SuccessResponse.created(mapper.model(model)));
    }


    @GetMapping("/manual-pdf-families")
    public ResponseEntity<SuccessResponse<List<ManualPdfFamilyResponse>>> listAllFamilies() {
        List<ManualPdfFamilyResponse> payload = service.getAllFamilies().stream().map(mapper::family).toList();
        return ResponseEntity.ok(SuccessResponse.ok(payload));
    }

    @GetMapping("/manual-pdf-models")
    public ResponseEntity<SuccessResponse<List<ManualPdfModelResponse>>> listModelsByFamily(
            @RequestParam Long familyId
    ) {
        List<ManualPdfModelResponse> payload = service.getModelsByFamily(familyId).stream().map(mapper::model).toList();
        return ResponseEntity.ok(SuccessResponse.ok(payload));
    }

    @GetMapping("/manual-pdfs/years")
    public ResponseEntity<SuccessResponse<List<Integer>>> years() {
        return ResponseEntity.ok(SuccessResponse.ok(service.getYears()));
    }

    @GetMapping("/manual-pdfs/families")
    public ResponseEntity<SuccessResponse<List<ManualPdfFamilyResponse>>> families() {
        List<ManualPdfFamilyResponse> payload = service.getFamilies().stream().map(mapper::family).toList();
        return ResponseEntity.ok(SuccessResponse.ok(payload));
    }

    @GetMapping("/manual-pdfs/models")
    public ResponseEntity<SuccessResponse<List<ManualPdfDocumentResponse>>> models(
            @RequestParam Long familyId
    ) {
        List<ManualPdfDocumentResponse> payload = service.getModelDetailsByFamily(familyId).stream()
                .map(mapper::documentDetail)
                .toList();
        return ResponseEntity.ok(SuccessResponse.ok(payload));
    }

    @GetMapping("/manual-pdfs")
    public ResponseEntity<SuccessResponse<List<ManualPdfDocumentResponse>>> documentsByModel(
            @RequestParam Long modelId
    ) {
        List<ManualPdfDocumentResponse> payload = service.getDocumentsByModel(modelId).stream()
                .map(mapper::documentDetail)
                .toList();
        return ResponseEntity.ok(SuccessResponse.ok(payload));
    }

    @GetMapping("/manual-pdfs/document")
    public ResponseEntity<SuccessResponse<ManualPdfDocumentResponse>> documentByFilter(
            @RequestParam int year,
            @RequestParam Long modelId
    ) {
        ManualPdfDocument document = service.getDocumentByFilter(year, modelId);
        List<ManualPdfImage> images = service.getImages(document.id());
        return ResponseEntity.ok(SuccessResponse.ok(mapper.document(document, images)));
    }

    @GetMapping("/manual-pdfs/{id}")
    public ResponseEntity<SuccessResponse<ManualPdfDocumentResponse>> documentById(@PathVariable Long id) {
        ManualPdfDocument document = service.getDocumentById(id);
        List<ManualPdfImage> images = service.getImages(document.id());
        return ResponseEntity.ok(SuccessResponse.ok(mapper.document(document, images)));
    }

    @PostMapping(value = "/manual-pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<ManualPdfDocumentResponse>> createDocument(
            @RequestParam Long modelId,
            @RequestParam String title,
            @RequestParam Integer yearFrom,
            @RequestParam Integer yearTo,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "images", required = false) List<MultipartFile> images
    ) {
        ManualPdfDocument document = service.createDocument(modelId, title, yearFrom, yearTo, file, images);
        List<ManualPdfImage> storedImages = service.getImages(document.id());
        return ResponseEntity.status(201).body(SuccessResponse.created(mapper.document(document, storedImages)));
    }

    @PatchMapping(value = "/manual-pdfs/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<ManualPdfDocumentResponse>> updateDocument(
            @PathVariable Long id,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "newImages", required = false) List<MultipartFile> newImages,
            @RequestParam(required = false) List<Long> removeImageIds
    ) {
        ManualPdfDocument document = service.updateDocument(id, modelId, title, yearFrom, yearTo, file, newImages, removeImageIds);
        List<ManualPdfImage> images = service.getImages(document.id());
        return ResponseEntity.ok(SuccessResponse.ok(mapper.document(document, images)));
    }

    @GetMapping(value = "/manual-pdfs/{documentId}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> preview(@PathVariable Long documentId) {
        ManualPdfFile file = service.loadPdf(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.fileName() + "\"")
                .body(file.resource());
    }

    @GetMapping(value = "/manual-pdfs/{documentId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> download(@PathVariable Long documentId) {
        ManualPdfFile file = service.loadPdf(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.resource());
    }

    @GetMapping("/manual-pdfs/{documentId}/images/{imageId}/view")
    public ResponseEntity<Resource> viewImage(
            @PathVariable Long documentId,
            @PathVariable Long imageId
    ) {
        ManualPdfFile file = service.loadImage(documentId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.fileName() + "\"")
                .body(file.resource());
    }
}