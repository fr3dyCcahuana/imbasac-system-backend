package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSalesDetail;

public interface ProductSalesDetailRepository {

    Page<ProductSalesDetail> findPage(
            String query,
            String category,
            boolean onlyWithStock,
            String priceList,
            String context,
            Pageable pageable
    );
}
