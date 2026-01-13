package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProformaV2Service implements GetProformaV2UseCase {

    private final ProformaRepository proformaRepository;
    private final ProformaItemRepository proformaItemRepository;

    @Override
    public ProformaV2Response getById(Long proformaId) {
        Proforma p = proformaRepository.findById(proformaId)
                .orElseThrow(() -> new InvalidProformaV2Exception("Proforma no encontrada: " + proformaId));
        List<ProformaItem> items = proformaItemRepository.findByProformaId(proformaId);
        return ProformaMapper.toResponse(p, items);
    }
}
