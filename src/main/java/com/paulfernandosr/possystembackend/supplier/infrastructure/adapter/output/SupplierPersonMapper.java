package com.paulfernandosr.possystembackend.supplier.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPerson;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;

public class SupplierPersonMapper {

    public static Supplier toSupplier(JuridicalPerson juridicalPerson) {
        return Supplier.builder()
                .legalName(juridicalPerson.getBusinessName())
                .documentType(DocumentType.RUC)
                .documentNumber(juridicalPerson.getDocumentNumber())
                .address(juridicalPerson.getAddress())
                .build();
    }
}
