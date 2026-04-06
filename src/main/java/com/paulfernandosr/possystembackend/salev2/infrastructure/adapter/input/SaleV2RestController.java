package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.salev2.domain.model.VoidSaleV2Response;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.AdminEditSaleV2BeforeSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSalesV2PageUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitSaleV2ToSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.VoidSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2AdminEditRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DetailResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SummaryResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.VoidSaleV2Request;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/sales/v2")
@RequiredArgsConstructor
public class SaleV2RestController {

    private final CreateSaleV2UseCase createSaleV2UseCase;
    private final AdminEditSaleV2BeforeSunatUseCase adminEditSaleV2BeforeSunatUseCase;
    private final VoidSaleV2UseCase voidSaleV2UseCase;
    private final GetSalesV2PageUseCase getSalesV2PageUseCase;
    private final GetSaleV2UseCase getSaleV2UseCase;
    private final EmitSaleV2ToSunatUseCase emitSaleV2ToSunatUseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<SaleV2DocumentResponse>> create(@RequestBody SaleV2CreateRequest request,
                                                                          Principal principal) {
        SaleV2DocumentResponse document = createSaleV2UseCase.create(request, principal.getName());
        return ResponseEntity
                .created(URI.create("/sales/v2/" + document.getSaleId()))
                .body(SuccessResponse.ok(document));
    }

    /**
     * Edición administrativa previa a SUNAT.
     * - Solo aplica a ventas ya registradas y todavía no enviadas a SUNAT.
     * - Reversa stock/seriales de los ítems anteriores y vuelve a grabar el detalle.
     * - No toca acumulados de caja ni sale_session.
     */
    @PatchMapping("/{saleId}/admin-edit-before-sunat")
    public ResponseEntity<SuccessResponse<SaleV2DocumentResponse>> adminEditBeforeSunat(@PathVariable Long saleId,
                                                                                         @RequestBody SaleV2AdminEditRequest request,
                                                                                         Principal principal) {
        SaleV2DocumentResponse response = adminEditSaleV2BeforeSunatUseCase.edit(saleId, request, principal.getName());
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    /**
     * Anulación de venta con reversa de stock/kardex/seriales y ajuste de CxC.
     * Regla clave: si es serial, el status queda en DEVUELTO.
     */
    @PostMapping("/{saleId}/void")
    public ResponseEntity<SuccessResponse<VoidSaleV2Response>> voidSale(@PathVariable Long saleId,
                                                                        @RequestBody(required = false) VoidSaleV2Request request,
                                                                        Principal principal) {
        String reason = request == null ? null : request.getReason();
        VoidSaleV2Response response = voidSaleV2UseCase.voidSale(saleId, reason, principal.getName());
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<SaleV2SummaryResponse>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) Long number,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sunatStatus,
            @RequestParam(required = false) String editStatus,
            @RequestParam(required = false) String paymentType
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                getSalesV2PageUseCase.findPage(query, docType, series, number, status, sunatStatus, editStatus, paymentType, page, size)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<SaleV2DetailResponse>> getById(@PathVariable("id") Long saleId) {
        return ResponseEntity.ok(SuccessResponse.ok(getSaleV2UseCase.getById(saleId)));
    }

    @PostMapping("/{saleId}/emit-sunat")
    public ResponseEntity<SuccessResponse<SaleV2SunatEmissionResponse>> emitSunat(@PathVariable Long saleId) {
        return ResponseEntity.ok(SuccessResponse.ok(emitSaleV2ToSunatUseCase.emit(saleId)));
    }
}
