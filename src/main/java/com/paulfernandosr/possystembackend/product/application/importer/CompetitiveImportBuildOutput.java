package com.paulfernandosr.possystembackend.product.application.importer;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportPreviewRow;

import java.util.List;
import java.util.Set;

public record CompetitiveImportBuildOutput(
        List<Product> commands,
        List<ProductCompetitiveImportPreviewRow> previewRows,
        Set<String> categoriesUsed
) {}
