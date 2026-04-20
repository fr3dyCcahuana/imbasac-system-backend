package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductPriceTypeInfo;
import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePrice;
import com.paulfernandosr.possystembackend.product.domain.ProductPurchasePricesInfo;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductPurchasePricesUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductPurchasePriceRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProductPurchasePricesService implements GetProductPurchasePricesUseCase {

    private final ProductRepository productRepository;
    private final ProductPurchasePriceRepository productPurchasePriceRepository;

    @Override
    public ProductPurchasePricesInfo getByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Producto no encontrado. id=" + productId));

        List<ProductPurchasePrice> purchasePrices = productPurchasePriceRepository.findLatest10ByProductId(productId);

        List<ProductPriceTypeInfo> priceTypes = List.of(
                ProductPriceTypeInfo.builder()
                        .code("A")
                        .name("Precio A")
                        .description("Precio de venta A configurado en el producto.")
                        .currentValue(product.getPriceA())
                        .build(),
                ProductPriceTypeInfo.builder()
                        .code("B")
                        .name("Precio B")
                        .description("Precio de venta B configurado en el producto.")
                        .currentValue(product.getPriceB())
                        .build(),
                ProductPriceTypeInfo.builder()
                        .code("C")
                        .name("Precio C")
                        .description("Precio de venta C configurado en el producto.")
                        .currentValue(product.getPriceC())
                        .build(),
                ProductPriceTypeInfo.builder()
                        .code("D")
                        .name("Precio D")
                        .description("Precio de venta D configurado en el producto.")
                        .currentValue(product.getPriceD())
                        .build(),
                ProductPriceTypeInfo.builder()
                        .code("COST_REFERENCE")
                        .name("Costo de referencia")
                        .description("Costo base de referencia guardado en el producto.")
                        .currentValue(product.getCostReference())
                        .build()
        );

        return ProductPurchasePricesInfo.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .brand(product.getBrand())
                .model(product.getModel())
                .category(product.getCategory())
                .purchasePrices(purchasePrices)
                .priceTypes(priceTypes)
                .build();
    }
}
