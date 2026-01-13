package com.paulfernandosr.possystembackend.sale.domain.port.output;

import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleDocument;

public interface DocumentIssuer {
    SaleDocument issueSimpleDocument(Sale sale);
    SaleDocument issueElectronicDocument(Sale sale);
}
