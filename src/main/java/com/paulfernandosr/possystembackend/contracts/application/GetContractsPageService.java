package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.port.input.GetContractsPageUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractQueryRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractSummaryResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageMetadata;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetContractsPageService implements GetContractsPageUseCase {

    private final ContractQueryRepository queryRepository;

    @Override
    public PageResponse<ContractSummaryResponse> findPage(String query, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String like = toLike(query);

        long total = queryRepository.count(like, status);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<ContractSummaryResponse> rows = queryRepository.findPage(like, status, safeSize, safePage * safeSize);

        PageMetadata meta = PageMetadata.builder()
                .page(safePage)
                .size(safeSize)
                .numberOfElements(rows.size())
                .totalElements(total)
                .totalPages(totalPages)
                .build();

        return PageResponse.<ContractSummaryResponse>builder()
                .payload(rows)
                .metadata(meta)
                .build();
    }

    private String toLike(String q) {
        if (q == null || q.trim().isBlank()) return "%";
        return "%" + q.trim() + "%";
    }
}
