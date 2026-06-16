package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.application.reference.ProductReferenceInfoExcelGenerator;
import com.paulfernandosr.possystembackend.product.application.reference.ProductReferenceSkuWorkbookParser;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.input.ExportProductReferenceInfoUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportProductReferenceInfoService implements ExportProductReferenceInfoUseCase {

    private final ProductRepository productRepository;

    @Override
    public byte[] exportFromSkuWorkbook(byte[] fileBytes, String originalFilename) {
        List<String> requestedSkus = ProductReferenceSkuWorkbookParser.parseSkus(fileBytes);
        Collection<String> uniqueSkus = requestedSkus.stream()
                .map(ExportProductReferenceInfoService::normalizeKey)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Product> productsBySku = productRepository.findBySkuIn(uniqueSkus)
                .stream()
                .filter(p -> p.getSku() != null)
                .collect(Collectors.toMap(
                        p -> normalizeKey(p.getSku()),
                        p -> p,
                        (first, ignored) -> first
                ));

        return ProductReferenceInfoExcelGenerator.generate(requestedSkus, productsBySku);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
