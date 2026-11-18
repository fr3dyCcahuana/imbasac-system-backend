package com.paulfernandosr.possystembackend.reports.domain.port.input;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.SlowMovingProduct;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetSlowMovingProductsUseCase {
    record Preview(List<SlowMovingProduct> items, boolean hasMore, OffsetDateTime generatedAt) {}
    Preview preview(SlowMovingFilter filter, int limit);
    byte[] excel(SlowMovingFilter filter);
    List<String> models();
}
