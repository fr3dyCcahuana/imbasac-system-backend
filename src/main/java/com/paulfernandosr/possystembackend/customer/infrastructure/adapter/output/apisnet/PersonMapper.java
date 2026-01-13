package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;

public class PersonMapper {
    public static Customer toCustomer(NaturalPerson naturalPerson) {
        return Customer.builder()
                .legalName(naturalPerson.getGivenNames() + " " + naturalPerson.getLastName() + " " + naturalPerson.getSecondLastName())
                .documentType(DocumentType.DNI)
                .documentNumber(naturalPerson.getDocumentNumber())
                .build();
    }

    public static Customer toCustomer(JuridicalPerson juridicalPerson) {
        return Customer.builder()
                .legalName(juridicalPerson.getBusinessName())
                .documentType(DocumentType.RUC)
                .documentNumber(juridicalPerson.getDocumentNumber())
                .address(juridicalPerson.getAddress())
                .build();
    }
}
