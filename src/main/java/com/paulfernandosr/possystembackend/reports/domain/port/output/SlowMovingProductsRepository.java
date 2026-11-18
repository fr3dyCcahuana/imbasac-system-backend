package com.paulfernandosr.possystembackend.reports.domain.port.output;

import com.paulfernandosr.possystembackend.reports.domain.SlowMovingFilter;
import com.paulfernandosr.possystembackend.reports.domain.SlowMovingProduct;

import java.util.List;

public interface SlowMovingProductsRepository {
    List<SlowMovingProduct> find(SlowMovingFilter filter, Integer limit);
    List<String> findModels();
}
