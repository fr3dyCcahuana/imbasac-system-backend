package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;

public interface ProductSerialUnitRepository {

    ProductSerialUnit create(ProductSerialUnit unit);

    Page<ProductSerialUnit> findPage(Long productId, String query, String status, Pageable pageable);
}
