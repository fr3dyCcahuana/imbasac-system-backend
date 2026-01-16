package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSalesDetail;

public interface GetProductSalesDetailPageUseCase {

    Page<ProductSalesDetail> getPage(
            String query,
            String category,
            boolean onlyWithStock,
            String priceList,
            String context,
            Pageable pageable
    );
}
