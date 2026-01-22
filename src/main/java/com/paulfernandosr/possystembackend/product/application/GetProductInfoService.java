package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductInfoUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductImageRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleSpecsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class GetProductInfoService implements GetProductInfoUseCase {

    private final ProductRepository productRepository;
    private final ProductVehicleSpecsRepository specsRepository;
    private final ProductImageRepository productImageRepository;

    @Override
    public Product getProductInfoById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with identification: " + productId
                ));

        // Carga opcional de ficha técnica (sin afectar listados paginados)
        specsRepository.findByProductId(productId)
                .ifPresent(product::setVehicleSpecs);

        // Carga de imágenes asociadas (detalle del producto)
        product.setImages(new ArrayList<>(productImageRepository.findByProductId(productId)));

        return product;
    }
}
