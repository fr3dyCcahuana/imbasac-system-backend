package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetPageOfProductsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPageOfProductsService implements GetPageOfProductsUseCase {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    @Override
    public Page<Product> getPageOfProducts(String query, Pageable pageable) {
        Page<Product> pageOfProducts = productRepository.findPage(query, pageable);

        // Enriquecer con imágenes sin N+1:
        // 1 query para productos + 1 query para imágenes del set de ids de la página.
        List<Product> products = (List<Product>) pageOfProducts.getContent();
        if (products == null || products.isEmpty()) {
            return pageOfProducts;
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .toList();

        if (productIds.isEmpty()) {
            return pageOfProducts;
        }

        Map<Long, List<com.paulfernandosr.possystembackend.product.domain.ProductImage>> imagesByProductId =
                productImageRepository.findByProductIds(productIds).stream()
                        .collect(Collectors.groupingBy(com.paulfernandosr.possystembackend.product.domain.ProductImage::getProductId));

        for (Product p : products) {
            p.setImages(imagesByProductId.getOrDefault(p.getId(), List.of()));
        }

        return pageOfProducts;
    }
}
