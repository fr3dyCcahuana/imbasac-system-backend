package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.proformav2.domain.model.VoidProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.CreateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.ConvertProformaToSaleV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformasV2PageUseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.VoidProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.VoidProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/proformas/v2")
@RequiredArgsConstructor
public class ProformaV2Controller {

    private final CreateProformaV2UseCase createUseCase;
    private final GetProformaV2UseCase getUseCase;
    private final ConvertProformaToSaleV2UseCase convertUseCase;
    private final VoidProformaV2UseCase voidUseCase;
    private final GetProformasV2PageUseCase getProformasV2PageUseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<ProformaV2Response>> create(@RequestBody CreateProformaV2Request request) {
        ProformaV2Response created = createUseCase.create(request);
        return ResponseEntity
                .created(URI.create("/proformas/v2/" + created.getId()))
                .body(SuccessResponse.ok(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<ProformaV2Response>> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(getUseCase.getById(id)));
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<SuccessResponse<ConvertProformaV2Response>> convert(
            @PathVariable("id") Long id,
            @RequestBody ConvertProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        ConvertProformaV2Response response = convertUseCase.convert(id, request, username);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                getProformasV2PageUseCase.findPage(status, query, page, size)
        ));
    }
}
