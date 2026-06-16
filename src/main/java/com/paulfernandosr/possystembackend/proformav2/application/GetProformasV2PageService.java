package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformasV2PageUseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaV2QueryRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProformasV2PageService implements GetProformasV2PageUseCase {

    private final ProformaV2QueryRepository queryRepository;

    @Override
    public PageResponse<ProformaV2SummaryResponse> findPage(
            String status,
            String query,
            Long createdBy,
            Long createdByRoleId,
            Boolean edited,
            String paymentType,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        ProformaQueryFilters filters = new ProformaQueryFilters(
                normalizeStatus(status),
                toLike(query),
                createdBy,
                createdByRoleId,
                edited,
                normalizeUpper(paymentType),
                dateFrom,
                dateTo
        );

        long total = queryRepository.countPage(filters);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<ProformaV2SummaryResponse> rows = queryRepository.findPage(filters, safeSize, safePage * safeSize);

        PageMetadata meta = PageMetadata.builder()
                .page(safePage)
                .size(safeSize)
                .numberOfElements(rows.size())
                .totalElements(total)
                .totalPages(totalPages)
                .build();

        return PageResponse.<ProformaV2SummaryResponse>builder()
                .payload(rows)
                .metadata(meta)
                .build();
    }

    @Override
    public List<ProformaCreatorResponse> findCreators() {
        return queryRepository.findCreators();
    }

    @Override
    public List<ProformaCreatorRoleResponse> findCreatorRoles() {
        return queryRepository.findCreatorRoles();
    }

    private String toLike(String q) {
        if (q == null || q.trim().isBlank()) return "%";
        return "%" + q.trim() + "%";
    }

    private String normalizeStatus(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().toUpperCase();
    }

    private String normalizeUpper(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().toUpperCase();
    }
}
