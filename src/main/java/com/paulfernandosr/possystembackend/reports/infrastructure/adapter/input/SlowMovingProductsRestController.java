package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.port.input.GetSlowMovingProductsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports/products/slow-moving")
@RequiredArgsConstructor
public class SlowMovingProductsRestController {
    private final GetSlowMovingProductsUseCase reports;

    @GetMapping("/models")
    public List<String> models() { return reports.models(); }

    @GetMapping
    public ResponseEntity<GetSlowMovingProductsUseCase.Preview> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "ALL") SlowMovingFilter.Status status,
            @RequestParam(defaultValue = "SALES") SlowMovingFilter.Sort sort,
            @RequestParam(defaultValue = "true") boolean stockOnly,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reports.preview(new SlowMovingFilter(from, to, model.trim(), status, sort, stockOnly), limit));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "") String model,
            @RequestParam(defaultValue = "ALL") SlowMovingFilter.Status status,
            @RequestParam(defaultValue = "SALES") SlowMovingFilter.Sort sort,
            @RequestParam(defaultValue = "true") boolean stockOnly) {
        byte[] body = reports.excel(new SlowMovingFilter(from, to, model.trim(), status, sort, stockOnly));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("productos_poca_rotacion_" + from + "_" + to + ".xlsx").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> invalidFilters(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Filtros inválidos. Revisa las fechas, el estado y el orden elegidos."));
    }
}
