package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformasV2PageUseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaV2QueryRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProformasV2PageService implements GetProformasV2PageUseCase {

    private final ProformaV2QueryRepository queryRepository;

    @Override
    public PageResponse<ProformaV2SummaryResponse> findPage(String status, String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String st = normalizeStatus(status);
        String like = toLike(query);

        long total = queryRepository.countPage(st, like);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<ProformaV2SummaryResponse> rows = queryRepository.findPage(st, like, safeSize, safePage * safeSize);

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

    private String toLike(String q) {
        if (q == null || q.trim().isBlank()) return "%";
        return "%" + q.trim() + "%";
    }

    private String normalizeStatus(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim().toUpperCase();
    }
}
