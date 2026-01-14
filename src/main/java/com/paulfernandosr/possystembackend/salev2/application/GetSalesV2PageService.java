package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSalesV2PageUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2QueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSalesV2PageService implements GetSalesV2PageUseCase {

    private final SaleV2QueryRepository saleV2QueryRepository;

    @Override
    public PageResponse<SaleV2SummaryResponse> findPage(String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String like = toLike(query);

        long total = saleV2QueryRepository.countSales(like);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<SaleV2SummaryResponse> rows = saleV2QueryRepository.findSalesPage(like, safeSize, safePage * safeSize);

        PageMetadata meta = PageMetadata.builder()
                .page(safePage)
                .size(safeSize)
                .numberOfElements(rows.size())
                .totalElements(total)
                .totalPages(totalPages)
                .build();

        return PageResponse.<SaleV2SummaryResponse>builder()
                .payload(rows)
                .metadata(meta)
                .build();
    }

    private String toLike(String q) {
        if (q == null || q.trim().isBlank()) return "%";
        return "%" + q.trim() + "%";
    }
}
