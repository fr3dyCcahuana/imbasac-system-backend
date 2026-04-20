package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePricesInfo;

public interface GetProductPurchasePricesUseCase {

    ProductPurchasePricesInfo getByProductId(Long productId);
}
