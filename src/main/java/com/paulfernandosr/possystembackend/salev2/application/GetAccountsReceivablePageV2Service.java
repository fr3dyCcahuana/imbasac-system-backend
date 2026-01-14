package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetAccountsReceivablePageV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivableQueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAccountsReceivablePageV2Service implements GetAccountsReceivablePageV2UseCase {

    private final AccountsReceivableQueryRepository queryRepository;

    @Override
    public PageResponse<AccountsReceivableSummaryResponse> findPage(Long customerId, String status, String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String like = toLike(query);
        String st = normalizeStatus(status);

        long total = queryRepository.countPage(customerId, st, like);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<AccountsReceivableSummaryResponse> rows =
                queryRepository.findPage(customerId, st, like, safeSize, safePage * safeSize);

        PageMetadata meta = PageMetadata.builder()
                .page(safePage)
                .size(safeSize)
                .numberOfElements(rows.size())
                .totalElements(total)
                .totalPages(totalPages)
                .build();

        return PageResponse.<AccountsReceivableSummaryResponse>builder()
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
