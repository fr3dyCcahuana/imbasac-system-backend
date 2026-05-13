package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.*;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.*;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.PreviewContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.SaveContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.SunatFilePublicUrlService;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftPreviewResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractRestController {

    private final CreateContractUseCase createContractUseCase;
    private final UpdateContractUseCase updateContractUseCase;
    private final ConfirmContractUseCase confirmContractUseCase;
    private final ActivateContractUseCase activateContractUseCase;
    private final VoidContractUseCase voidContractUseCase;
    private final ResolveContractRepossessionUseCase resolveContractRepossessionUseCase;
    private final ReleaseRecoveredContractUnitUseCase releaseRecoveredContractUnitUseCase;
    private final GetContractsPageUseCase getContractsPageUseCase;
    private final GetContractUseCase getContractUseCase;
    private final GenerateSaleFromContractUseCase generateSaleFromContractUseCase;
    private final PayContractInstallmentUseCase payContractInstallmentUseCase;

    private final GetContractSunatDraftUseCase getContractSunatDraftUseCase;
    private final SaveContractSunatDraftUseCase saveContractSunatDraftUseCase;
    private final PreviewContractSunatDraftUseCase previewContractSunatDraftUseCase;
    private final EmitContractSunatDraftUseCase emitContractSunatDraftUseCase;
    private final SunatFilePublicUrlService sunatFilePublicUrlService;

    @PostMapping
    public ResponseEntity<SuccessResponse<ContractDocumentResponse>> create(@RequestBody ContractCreateRequest request,
                                                                           Principal principal) {
        ContractDocumentResponse doc = createContractUseCase.create(request, principal.getName());
        return ResponseEntity
                .created(URI.create("/contracts/" + doc.getContractId()))
                .body(SuccessResponse.ok(doc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<ContractDetailResponse>> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(getContractUseCase.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<ContractDetailResponse>> update(@PathVariable("id") Long id,
                                                                          @RequestBody ContractUpdateRequest request,
                                                                          Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                updateContractUseCase.update(id, request, principal.getName())
        ));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ContractSummaryResponse>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(getContractsPageUseCase.findPage(query, status, page, size)));
    }

    /**
     * Solo confirma contratos CONTADO. Para CREDITO usar /activate porque debe registrar el inicial.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<SuccessResponse<ContractDetailResponse>> confirm(@PathVariable("id") Long id, Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(confirmContractUseCase.confirm(id, principal.getName())));
    }

    /**
     * Activa el contrato:
     * - CONTADO: queda CONFIRMADO.
     * - CREDITO: registra inicial y queda CREDITO_ACTIVO.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<SuccessResponse<ContractDetailResponse>> activate(@PathVariable("id") Long id,
                                                                            @RequestBody(required = false) ContractActivateRequest request,
                                                                            Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(activateContractUseCase.activate(id, request, principal.getName())));
    }

    /**
     * Compatibilidad: endpoint viejo. Ya no genera venta interna ni reserva B/F.
     */
    @PostMapping("/{id}/sales")
    public ResponseEntity<SuccessResponse<ContractGenerateSaleResponse>> generateSale(@PathVariable("id") Long id,
                                                                                      @RequestBody(required = false) ContractGenerateSaleRequest request,
                                                                                      Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                generateSaleFromContractUseCase.generateSale(id, request, principal.getName())
        ));
    }

    @PostMapping("/{id}/installments/{n}/payments")
    public ResponseEntity<SuccessResponse<ContractInstallmentPaymentResponse>> payInstallment(
            @PathVariable("id") Long id,
            @PathVariable("n") int n,
            @RequestBody ContractInstallmentPaymentRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                payContractInstallmentUseCase.pay(id, n, request, principal.getName())
        ));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<SuccessResponse<ContractVoidResponse>> voidContract(@PathVariable("id") Long id,
                                                                              @RequestBody(required = false) ContractVoidRequest request,
                                                                              Principal principal) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(voidContractUseCase.voidContract(id, reason, principal.getName())));
    }

    @PostMapping("/{id}/resolve-repossession")
    public ResponseEntity<SuccessResponse<ContractVoidResponse>> resolveRepossession(@PathVariable("id") Long id,
                                                                                     @RequestBody ContractRepossessionRequest request,
                                                                                     Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(resolveContractRepossessionUseCase.resolve(id, request, principal.getName())));
    }

    @PostMapping("/{id}/release-recovered-unit")
    public ResponseEntity<SuccessResponse<ContractVoidResponse>> releaseRecoveredUnit(@PathVariable("id") Long id,
                                                                                      @RequestBody(required = false) ContractVoidRequest request,
                                                                                      Principal principal) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(releaseRecoveredContractUnitUseCase.release(id, reason, principal.getName())));
    }

    @GetMapping("/{id}/sunat-draft")
    public ResponseEntity<SuccessResponse<ContractSunatDraftResponse>> getContractSunatDraft(@PathVariable("id") Long contractId,
                                                                                              Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                getContractSunatDraftUseCase.getOrCreate(contractId, principal.getName())
        ));
    }

    @PutMapping("/{id}/sunat-draft")
    public ResponseEntity<SuccessResponse<ContractSunatDraftResponse>> saveContractSunatDraft(@PathVariable("id") Long contractId,
                                                                                               @RequestBody ContractSunatDraftSaveRequest request,
                                                                                               Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                saveContractSunatDraftUseCase.save(contractId, request, principal.getName())
        ));
    }

    @PostMapping("/{id}/sunat-draft/preview")
    public ResponseEntity<SuccessResponse<ContractSunatDraftPreviewResponse>> previewContractSunatDraft(@PathVariable("id") Long contractId,
                                                                                                         @RequestBody ContractSunatDraftSaveRequest request) {
        return ResponseEntity.ok(SuccessResponse.ok(
                previewContractSunatDraftUseCase.preview(contractId, request)
        ));
    }

    @PostMapping("/{id}/sunat-draft/emit")
    public ResponseEntity<SuccessResponse<ContractSunatDraftEmissionResponse>> emitContractSunatDraft(@PathVariable("id") Long contractId,
                                                                                                      Principal principal) {
        ContractSunatDraftEmissionResponse response =
                emitContractSunatDraftUseCase.emit(contractId, principal.getName());

        sunatFilePublicUrlService.enrich(response);

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
