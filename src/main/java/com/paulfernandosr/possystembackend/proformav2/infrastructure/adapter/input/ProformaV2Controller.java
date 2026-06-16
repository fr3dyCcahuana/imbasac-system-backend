package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.proformav2.domain.model.VoidProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.CreateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.ConvertProformaToSaleV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformasV2PageUseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.UpdateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.VoidProformaV2UseCase;
import com.paulfernandosr.possystembackend.stockreservation.domain.port.input.ExpireStockReservationsUseCase;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.VoidProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.UpdateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorRoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/proformas/v2")
@RequiredArgsConstructor
public class ProformaV2Controller {

    private final CreateProformaV2UseCase createUseCase;
    private final GetProformaV2UseCase getUseCase;
    private final ConvertProformaToSaleV2UseCase convertUseCase;
    private final VoidProformaV2UseCase voidUseCase;
    private final GetProformasV2PageUseCase getProformasV2PageUseCase;
    private final UpdateProformaV2UseCase updateUseCase;
    private final ExpireStockReservationsUseCase expireStockReservationsUseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<ProformaV2Response>> create(
            @RequestBody CreateProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        ProformaV2Response created = createUseCase.create(request, username);
        return ResponseEntity
                .created(URI.create("/proformas/v2/" + created.getNumber()))
                .body(SuccessResponse.ok(created));
    }

    @GetMapping("/{number}")
    public ResponseEntity<SuccessResponse<ProformaV2Response>> get(@PathVariable("number") Long number) {
        return ResponseEntity.ok(SuccessResponse.ok(getUseCase.getByNumber(number)));
    }

    @PostMapping("/stock-reservations/expire")
    public ResponseEntity<SuccessResponse<Integer>> expireStockReservations() {
        return ResponseEntity.ok(SuccessResponse.ok(expireStockReservationsUseCase.expirePreviousDays()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<ProformaV2Response>> update(
            @PathVariable("id") Long id,
            @RequestBody UpdateProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(SuccessResponse.ok(updateUseCase.update(id, request, username)));
    }

    @PostMapping("/{number}/convert")
    public ResponseEntity<SuccessResponse<ConvertProformaV2Response>> convert(
            @PathVariable("number") Long number,
            @RequestBody ConvertProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        // Regla de negocio: el facturador convierte por NÚMERO visible de proforma.
        // El service resuelve internamente p.id para guardar la relación por ID real.
        ConvertProformaV2Response response = convertUseCase.convert(number, request, username);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    /**
     * Endpoint principal para anular una proforma.
     * Solo permite anular proformas en estado PENDIENTE.
     */
    @PostMapping("/{id}/anular")
    public ResponseEntity<SuccessResponse<VoidProformaV2Response>> anularProforma(
            @PathVariable("id") Long id,
            @RequestBody(required = false) VoidProformaV2Request request
    ) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(voidUseCase.voidProforma(id, reason)));
    }

    /**
     * Alias técnico mantenido por compatibilidad.
     * Se recomienda que el frontend use POST /proformas/v2/{id}/anular.
     */
    @PostMapping("/{id}/void")
    public ResponseEntity<SuccessResponse<VoidProformaV2Response>> voidProforma(
            @PathVariable("id") Long id,
            @RequestBody(required = false) VoidProformaV2Request request
    ) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(voidUseCase.voidProforma(id, reason)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ProformaV2SummaryResponse>>> findPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) Long createdByRoleId,
            @RequestParam(required = false) Boolean edited,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                getProformasV2PageUseCase.findPage(status, query, createdBy, createdByRoleId, edited, paymentType, dateFrom, dateTo, page, size)
        ));
    }

    @GetMapping("/creators")
    public ResponseEntity<SuccessResponse<List<ProformaCreatorResponse>>> findCreators() {
        return ResponseEntity.ok(SuccessResponse.ok(getProformasV2PageUseCase.findCreators()));
    }

    @GetMapping("/creator-roles")
    public ResponseEntity<SuccessResponse<List<ProformaCreatorRoleResponse>>> findCreatorRoles() {
        return ResponseEntity.ok(SuccessResponse.ok(getProformasV2PageUseCase.findCreatorRoles()));
    }
}
