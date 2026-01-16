package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSalesDetail;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductSalesDetailPageUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSalesDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetProductSalesDetailPageService implements GetProductSalesDetailPageUseCase {

    private static final Set<String> PRICE_LISTS = Set.of("A", "B", "C", "D");
    private static final Set<String> CONTEXTS = Set.of("PROFORMA", "SALE");

    private final ProductSalesDetailRepository repository;

    @Override
    public Page<ProductSalesDetail> getPage(
            String query,
            String category,
            boolean onlyWithStock,
            String priceList,
            String context,
            Pageable pageable
    ) {
        String pl = (priceList == null ? "A" : priceList.trim().toUpperCase());
        if (!PRICE_LISTS.contains(pl)) {
            throw new InvalidProductException("priceList inválido. Use A, B, C o D.");
        }

        String ctx = (context == null ? "PROFORMA" : context.trim().toUpperCase());
        if (!CONTEXTS.contains(ctx)) {
            throw new InvalidProductException("context inválido. Use PROFORMA o SALE.");
        }

        return repository.findPage(
                query == null ? "" : query,
                category == null ? "" : category,
                onlyWithStock,
                pl,
                ctx,
                pageable
        );
    }
}
