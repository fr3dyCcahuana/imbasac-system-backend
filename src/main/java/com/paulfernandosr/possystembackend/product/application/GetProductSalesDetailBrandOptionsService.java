package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductSalesDetailBrandOptionsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSalesDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetProductSalesDetailBrandOptionsService implements GetProductSalesDetailBrandOptionsUseCase {

    private static final Set<String> CONTEXTS = Set.of("PROFORMA", "SALE");

    private final ProductSalesDetailRepository repository;

    @Override
    public List<String> getBrands(String context) {
        String ctx = context == null ? "PROFORMA" : context.trim().toUpperCase();

        if (!CONTEXTS.contains(ctx)) {
            throw new InvalidProductException("context inválido. Use PROFORMA o SALE.");
        }

        return repository.findAvailableBrands(ctx);
    }
}