package com.paulfernandosr.possystembackend.product.domain.port.input;

import java.util.List;

public interface GetProductSalesDetailBrandOptionsUseCase {

    List<String> getBrands(String context);
}