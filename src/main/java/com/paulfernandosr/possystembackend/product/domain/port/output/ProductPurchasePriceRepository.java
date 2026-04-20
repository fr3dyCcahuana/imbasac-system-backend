package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePrice;

import java.util.List;

public interface ProductPurchasePriceRepository {

    List<ProductPurchasePrice> findLatest10ByProductId(Long productId);
}
