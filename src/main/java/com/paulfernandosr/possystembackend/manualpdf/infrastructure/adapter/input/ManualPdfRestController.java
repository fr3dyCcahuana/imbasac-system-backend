package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamilyUpsertCommand;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelUpsertCommand;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfRegistrationCommand;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfFamilyUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfModelUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfDocumentUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfFamiliesUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfModelsUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfYearsUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.PreviewManualPdfUseCase;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfCreateRequest;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfDocumentResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfFamilyCreateRequest;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfFamilyResponse;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfModelCreateRequest;
import com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto.ManualPdfModelResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/manual-pdfs")
public class ManualPdfRestController {

    private final GetManualPdfYearsUseCase getManualPdfYearsUseCase;
    private final GetManualPdfFamiliesUseCase getManualPdfFamiliesUseCase;
    private final GetManualPdfModelsUseCase getManualPdfModelsUseCase;
    private final GetManualPdfDocumentUseCase getManualPdfDocumentUseCase;
    private final PreviewManualPdfUseCase previewManualPdfUseCase;
    private final CreateManualPdfUseCase createManualPdfUseCase;
    private final CreateManualPdfFamilyUseCase createManualPdfFamilyUseCase;
    private final CreateManualPdfModelUseCase createManualPdfModelUseCase;
    private final ManualPdfPublicUrlService manualPdfPublicUrlService;

    @GetMapping("/years")
    public ResponseEntity<SuccessResponse<List<Integer>>> getYears() {
        return ResponseEntity.ok(SuccessResponse.ok(getManualPdfYearsUseCase.getYears()));
    }

    @GetMapping("/families")
    public ResponseEntity<SuccessResponse<List<ManualPdfFamilyResponse>>> getFamilies(@RequestParam int year) {
        List<ManualPdfFamilyResponse> response = getManualPdfFamiliesUseCase.getFamilies(year).stream()
                .map(item -> new ManualPdfFamilyResponse(item.id(), item.code(), item.name()))
                .toList();

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @PostMapping(value = "/families", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<ManualPdfFamilyResponse>> createFamily(
            @Valid @RequestBody ManualPdfFamilyCreateRequest request
    ) {
        ManualPdfFamily family = createManualPdfFamilyUseCase.create(new ManualPdfFamilyUpsertCommand(
                request.getCode(),
                request.getName(),
                request.getSortOrder(),
                request.getEnabled()
        ));

        return ResponseEntity.created(URI.create("/manual-pdfs/families/" + family.id()))
                .body(SuccessResponse.ok(new ManualPdfFamilyResponse(family.id(), family.code(), family.name())));
    }

    @GetMapping("/models")
    public ResponseEntity<SuccessResponse<List<ManualPdfModelResponse>>> getModels(
            @RequestParam int year,
            @RequestParam Long familyId
    ) {
        List<ManualPdfModelResponse> response = getManualPdfModelsUseCase.getModels(year, familyId).stream()
                .map(item -> new ManualPdfModelResponse(item.id(), item.familyId(), item.code(), item.name()))
                .toList();

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @PostMapping(value = "/models", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<ManualPdfModelResponse>> createModel(
            @Valid @RequestBody ManualPdfModelCreateRequest request
    ) {
        ManualPdfModel model = createManualPdfModelUseCase.create(new ManualPdfModelUpsertCommand(
                request.getFamilyId(),
                request.getCode(),
                request.getName(),
                request.getSortOrder(),
                request.getEnabled()
        ));

        return ResponseEntity.created(URI.create("/manual-pdfs/models/" + model.id()))
                .body(SuccessResponse.ok(new ManualPdfModelResponse(
                        model.id(),
                        model.familyId(),
                        model.code(),
                        model.name()
                )));
    }

    @GetMapping("/document")
    public ResponseEntity<SuccessResponse<ManualPdfDocumentResponse>> getDocument(
            @RequestParam int year,
            @RequestParam Long modelId
    ) {
        ManualPdfDocument document = getManualPdfDocumentUseCase.getDocument(year, modelId);
        return ResponseEntity.ok(SuccessResponse.ok(toResponse(document)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<ManualPdfDocumentResponse>> create(
            @Valid @ModelAttribute ManualPdfCreateRequest request
    ) {
        ManualPdfRegistrationCommand command = new ManualPdfRegistrationCommand(
                request.getModelId(),
                request.getTitle(),
                request.getYearFrom(),
                request.getYearTo(),
                request.getEnabled()
        );

        ManualPdfDocument created = createManualPdfUseCase.create(command, request.getFile());
        URI location = URI.create("/manual-pdfs/" + created.id() + "/preview");

        return ResponseEntity.created(location)
                .body(SuccessResponse.ok(toResponse(created)));
    }

    @GetMapping(value = "/{documentId}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> preview(@PathVariable Long documentId) {
        ManualPdfFile file = previewManualPdfUseCase.preview(documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.fileName() + "\"")
                .body(file.resource());
    }

    @GetMapping(value = "/{documentId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> download(@PathVariable Long documentId) {
        ManualPdfFile file = previewManualPdfUseCase.preview(documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.resource());
    }

    private ManualPdfDocumentResponse toResponse(ManualPdfDocument document) {
        return new ManualPdfDocumentResponse(
                document.id(),
                document.title(),
                document.fileName(),
                document.yearFrom(),
                document.yearTo(),
                document.enabled(),
                manualPdfPublicUrlService.buildPreviewUrl(document.id()),
                manualPdfPublicUrlService.buildDownloadUrl(document.id())
        );
    }
}
