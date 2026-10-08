package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.product.domain.ProductCatalogExcelExportRequest;
import com.paulfernandosr.possystembackend.product.domain.port.input.ExportProductCatalogExcelUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductCatalogExcelExportRestController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportProductCatalogExcelUseCase useCase;

    @PostMapping(
            value = "/exports/catalog-excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportCatalogExcel(@RequestBody(required = false) ProductCatalogExcelExportRequest request) {
        byte[] file = useCase.export(request);

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("productos.xlsx").build().toString())
                .body(file);
    }
}
