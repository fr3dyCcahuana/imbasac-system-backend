package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductKardexEntry;

import java.time.LocalDate;

public interface GetProductKardexPageUseCase {

    Page<ProductKardexEntry> getPage(
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
    );
}
