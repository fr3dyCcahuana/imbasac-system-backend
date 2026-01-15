package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;

public interface GetPageOfProductSerialUnitsUseCase {
    Page<ProductSerialUnit> getPage(Long productId, String query, String status, Pageable pageable);
}
