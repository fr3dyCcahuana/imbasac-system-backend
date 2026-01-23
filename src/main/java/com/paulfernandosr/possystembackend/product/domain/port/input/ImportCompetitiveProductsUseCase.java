package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportCommand;
import com.paulfernandosr.possystembackend.product.domain.ProductCompetitiveImportResult;

public interface ImportCompetitiveProductsUseCase {
    ProductCompetitiveImportResult importCompetitive(ProductCompetitiveImportCommand command);
}
