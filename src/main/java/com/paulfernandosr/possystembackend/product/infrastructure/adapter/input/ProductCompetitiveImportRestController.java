package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportCommand;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportResult;
import com.paulfernandosr.possystembackend.product.domain.port.input.ImportCompetitiveProductsUseCase;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.CompetitiveImportErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductCompetitiveImportRestController {

    private final ImportCompetitiveProductsUseCase useCase;

    @PostMapping(
            value = "/imports/competitive",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> importCompetitive(
            @RequestPart("file") MultipartFile file,
            @RequestParam BigDecimal montoRestaPublico,
            @RequestParam BigDecimal montoRestaMayorista,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "true") boolean atomic
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Debe enviar el archivo Excel en 'file'.");
        }
        if (montoRestaPublico == null || montoRestaPublico.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "montoRestaPublico debe ser >= 0.");
        }
        if (montoRestaMayorista == null || montoRestaMayorista.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "montoRestaMayorista debe ser >= 0.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo leer el archivo Excel.");
        }

        ProductCompetitiveImportCommand command = ProductCompetitiveImportCommand.builder()
                .fileBytes(bytes)
                .originalFilename(file.getOriginalFilename())
                .montoRestaPublico(montoRestaPublico)
                .montoRestaMayorista(montoRestaMayorista)
                .dryRun(dryRun)
                .atomic(atomic)
                .build();

        ProductCompetitiveImportResult result = useCase.importCompetitive(command);

        if (result.hasErrors()) {
            return ResponseEntity.unprocessableEntity()
                    .body(CompetitiveImportErrorResponse.from(result));
        }

        return ResponseEntity.ok(SuccessResponse.ok(result));
    }
}
