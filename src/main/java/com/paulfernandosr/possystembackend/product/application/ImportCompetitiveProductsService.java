package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.application.importer.*;
import com.paulfernandosr.possystembackend.product.domain.*;
import com.paulfernandosr.possystembackend.product.domain.port.input.ImportCompetitiveProductsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.CategoryWriteRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductBulkUpsertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportCompetitiveProductsService implements ImportCompetitiveProductsUseCase {

    private final ProductBulkUpsertRepository bulkRepo;
    private final CategoryWriteRepository categoryWriteRepository;

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.10");

    @Override
    public ProductCompetitiveImportResult importCompetitive(ProductCompetitiveImportCommand command) {
        ProductCompetitiveImportResult result = ProductCompetitiveImportResult.builder()
                .dryRun(Boolean.TRUE.equals(command.getDryRun()))
                .atomic(command.getAtomic() == null ? Boolean.TRUE : command.getAtomic())
                .minPrice(MIN_PRICE)
                .summary(ProductCompetitiveImportSummary.empty())
                .build();

        CompetitiveImportWorkbook workbook = CompetitiveImportExcelParser.parse(command, result);
        if (result.hasErrors()) {
            result.computeSummary();
            return result;
        }

        CompetitiveImportBuildOutput build = CompetitiveImportBuilder.validateAndBuild(
                workbook,
                command.getPctPublicA(), command.getPctPublicB(),
                command.getPctWholesaleC(), command.getPctWholesaleD(),
                MIN_PRICE,
                result
        );

        result.setPreview(build.previewRows());
        result.computeSummary();

        if (result.hasErrors()) return result;
        if (Boolean.TRUE.equals(command.getDryRun())) return result;

        boolean atomic = result.getAtomic() == null || result.getAtomic();

        if (atomic) {
            persistAtomic(build.commands(), build.categoriesUsed(), result);
        } else {
            persistNonAtomic(build.commands(), build.categoriesUsed(), result);
        }

        result.computeSummary();
        return result;
    }

    @Transactional
    protected void persistAtomic(List<Product> products, Set<String> categoriesUsed, ProductCompetitiveImportResult result) {
        categoryWriteRepository.insertMissing(categoriesUsed);

        Set<String> existing = bulkRepo.findExistingSkus(
                products.stream().map(Product::getSku).collect(Collectors.toSet())
        );

        int inserted = 0;
        int updated = 0;
        for (Product p : products) {
            bulkRepo.upsertBySku(p);
            if (existing.contains(p.getSku())) updated++;
            else inserted++;
        }

        result.getSummary().setInserted(inserted);
        result.getSummary().setUpdated(updated);
    }

    @Transactional
    protected void persistNonAtomic(List<Product> products, Set<String> categoriesUsed, ProductCompetitiveImportResult result) {
        categoryWriteRepository.insertMissing(categoriesUsed);

        Set<String> existing = bulkRepo.findExistingSkus(
                products.stream().map(Product::getSku).collect(Collectors.toSet())
        );

        int inserted = 0;
        int updated = 0;
        for (Product p : products) {
            bulkRepo.upsertBySku(p);
            if (existing.contains(p.getSku())) updated++;
            else inserted++;
        }

        result.getSummary().setInserted(inserted);
        result.getSummary().setUpdated(updated);
    }
}
