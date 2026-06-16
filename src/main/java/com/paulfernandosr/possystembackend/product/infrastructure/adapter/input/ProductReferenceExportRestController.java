package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.product.domain.port.input.ExportProductReferenceInfoUseCase;
import com.paulfernandosr.possystembackend.product.application.reference.ProductReferenceInfoTemplateGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductReferenceExportRestController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportProductReferenceInfoUseCase useCase;

    @GetMapping(
            value = "/exports/reference-info/template",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> downloadReferenceInfoTemplate() {
        byte[] bytes = ProductReferenceInfoTemplateGenerator.generate();

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_codigos_productos.xlsx\"")
                .body(bytes);
    }

    @PostMapping(
            value = "/exports/reference-info",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportReferenceInfo(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Debe enviar el archivo Excel en 'file'.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo leer el archivo Excel.");
        }

        byte[] result = useCase.exportFromSkuWorkbook(bytes, file.getOriginalFilename());

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"productos_referencial.xlsx\"")
                .body(result);
    }
}
