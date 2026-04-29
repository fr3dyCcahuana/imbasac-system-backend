package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexEntry;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductKardexPageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductKardexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetProductKardexPageService implements GetProductKardexPageUseCase {

    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("ALL", "ENTRADA", "SALIDA");
    private static final Set<String> ALLOWED_SOURCES = Set.of("ALL", "PURCHASE", "SALE", "COUNTER_SALE", "ADJUSTMENT");

    private final ProductKardexRepository repository;

    @Override
    public Page<ProductKardexEntry> getPage(
            String query,
            Long productId,
            String category,
            String brand,
            String model,
            String movementType,
            String direction,
            String source,
            String docType,
            String series,
            String number,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        String dir = normalizeEnum(direction, "ALL");
        if (!ALLOWED_DIRECTIONS.contains(dir)) {
            throw new InvalidProductException("direction inválido. Use ALL, ENTRADA o SALIDA.");
        }

        String src = normalizeEnum(source, "ALL");
        if (!ALLOWED_SOURCES.contains(src)) {
            throw new InvalidProductException("source inválido. Use ALL, PURCHASE, SALE, COUNTER_SALE o ADJUSTMENT.");
        }

        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new InvalidProductException("dateFrom no puede ser mayor que dateTo.");
        }

        return repository.findPage(
                safe(query),
                productId,
                safe(category),
                safe(brand),
                safe(model),
                normalizeEnum(movementType, ""),
                dir,
                src,
                normalizeEnum(docType, ""),
                safe(series),
                safe(number),
                dateFrom,
                dateTo,
                pageable
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEnum(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
