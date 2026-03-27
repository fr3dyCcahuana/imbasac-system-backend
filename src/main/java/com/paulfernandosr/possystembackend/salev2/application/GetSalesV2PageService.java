package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSalesV2PageUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2QueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageMetadata;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SummaryItemResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetSalesV2PageService implements GetSalesV2PageUseCase {

    private static final Set<String> ALLOWED_DOC_TYPES = Set.of("BOLETA", "FACTURA");

    private final SaleV2QueryRepository saleV2QueryRepository;

    @Override
    public PageResponse<SaleV2SummaryResponse> findPage(String query, String docType, String series, Long number, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        String like = toLike(query);
        String normalizedDocType = normalizeDocType(docType);
        String normalizedSeries = normalizeSeries(series);

        long total = saleV2QueryRepository.countSales(like, normalizedDocType, normalizedSeries, number);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        List<SaleV2SummaryResponse> rows = saleV2QueryRepository.findSalesPage(
                like,
                normalizedDocType,
                normalizedSeries,
                number,
                safeSize,
                safePage * safeSize
        );

        if (!rows.isEmpty()) {
            List<Long> saleIds = rows.stream()
                    .map(SaleV2SummaryResponse::getSaleId)
                    .toList();

            Map<Long, List<SaleV2SummaryItemResponse>> itemsBySaleId = saleV2QueryRepository.findSaleSummaryItemsBySaleIds(saleIds)
                    .stream()
                    .collect(Collectors.groupingBy(SaleV2SummaryItemResponse::getSaleId));

            rows.forEach(row -> row.setItems(itemsBySaleId.getOrDefault(row.getSaleId(), List.of())));
        }

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

    private String normalizeDocType(String docType) {
        if (docType == null || docType.trim().isBlank()) {
            return null;
        }

        String normalized = docType.trim().toUpperCase();
        if (!ALLOWED_DOC_TYPES.contains(normalized)) {
            throw new InvalidSaleV2Exception("docType solo permite BOLETA o FACTURA.");
        }

        return normalized;
    }

    private String normalizeSeries(String series) {
        if (series == null || series.trim().isBlank()) {
            return null;
        }
        return series.trim().toUpperCase();
    }
}