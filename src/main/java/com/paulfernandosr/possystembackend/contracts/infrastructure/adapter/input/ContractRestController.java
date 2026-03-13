package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.*;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.*;
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
    private final ConfirmContractUseCase confirmContractUseCase;
    private final VoidContractUseCase voidContractUseCase;
    private final GetContractsPageUseCase getContractsPageUseCase;
    private final GetContractUseCase getContractUseCase;
    private final GenerateSaleFromContractUseCase generateSaleFromContractUseCase;

    private final PayContractInstallmentUseCase payContractInstallmentUseCase;

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

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ContractSummaryResponse>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(getContractsPageUseCase.findPage(query, status, page, size)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<SuccessResponse<ContractDetailResponse>> confirm(@PathVariable("id") Long id, Principal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(confirmContractUseCase.confirm(id, principal.getName())));
    }

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
}
