package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.proformav2.domain.port.input.CreateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.ConvertProformaToSaleV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/proformas/v2")
public class ProformaV2Controller {

    private final CreateProformaV2UseCase createUseCase;
    private final GetProformaV2UseCase getUseCase;
    private final ConvertProformaToSaleV2UseCase convertUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProformaV2Response create(@RequestBody CreateProformaV2Request request) {
        return createUseCase.create(request);
    }

    @GetMapping("/{id}")
    public ProformaV2Response get(@PathVariable("id") Long id) {
        return getUseCase.getById(id);
    }

    @PostMapping("/{id}/convert")
    public ConvertProformaV2Response convert(@PathVariable("id") Long id,
                                             @RequestBody ConvertProformaV2Request request,
                                             Principal principal) {
        String username = principal != null ? principal.getName() : null;
        return convertUseCase.convert(id, request, username);
    }
}
