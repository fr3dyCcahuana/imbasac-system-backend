package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.input.GetCounterSalePageUseCase;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.CounterSaleQueryRepository;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetCounterSalePageService implements GetCounterSalePageUseCase {

    private static final Set<String> ALLOWED_STATUSES = Set.of("EMITIDA", "ANULADA");

    private final CounterSaleQueryRepository counterSaleQueryRepository;

    @Override
    public PageResponse<CounterSaleSummaryResponse> findPage(String query,
                                                             String series,
                                                             Long number,
                                                             String status,
                                                             int page,
                                                             int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String like = toLike(query);
        String normalizedSeries = normalizeSeries(series);
        String normalizedStatus = normalizeEnum(status, ALLOWED_STATUSES, "status", "EMITIDA o ANULADA");

        long total = counterSaleQueryRepository.countCounterSales(like, normalizedSeries, number, normalizedStatus);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<CounterSaleSummaryResponse> rows = counterSaleQueryRepository.findCounterSalesPage(
                like,
                normalizedSeries,
                number,
                normalizedStatus,
                safeSize,
                safePage * safeSize
        );

        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(CounterSaleSummaryResponse::getCounterSaleId).toList();
            Map<Long, List<CounterSaleSummaryItemResponse>> itemsById = counterSaleQueryRepository
                    .findSummaryItemsByCounterSaleIds(ids)
                    .stream()
                    .collect(Collectors.groupingBy(CounterSaleSummaryItemResponse::getCounterSaleId));
            rows.forEach(row -> row.setItems(itemsById.getOrDefault(row.getCounterSaleId(), List.of())));
        }

        return PageResponse.<CounterSaleSummaryResponse>builder()
                .payload(rows)
                .metadata(PageMetadata.builder()
                        .page(safePage)
                        .size(safeSize)
                        .numberOfElements(rows.size())
                        .totalElements(total)
                        .totalPages(totalPages)
                        .build())
                .build();
    }

    private String toLike(String q) {
        if (q == null || q.trim().isBlank()) return "%";
        return "%" + q.trim() + "%";
    }

    private String normalizeSeries(String series) {
        if (series == null || series.trim().isBlank()) return null;
        return series.trim().toUpperCase();
    }

    private String normalizeEnum(String value, Set<String> allowed, String fieldName, String allowedText) {
        if (value == null || value.trim().isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new InvalidCounterSaleException(fieldName + " solo permite " + allowedText + ".");
        }
        return normalized;
    }
}
