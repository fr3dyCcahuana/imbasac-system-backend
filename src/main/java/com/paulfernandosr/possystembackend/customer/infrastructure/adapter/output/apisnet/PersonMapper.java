package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PersonMapper {
    public static Customer toCustomer(NaturalPerson naturalPerson) {
        return Customer.builder()
                .legalName(naturalPerson.getGivenNames() + " " + naturalPerson.getLastName() + " " + naturalPerson.getSecondLastName())
                .documentType(DocumentType.DNI)
                .documentNumber(naturalPerson.getDocumentNumber())
                .build();
    }

    public static Customer toCustomer(JuridicalPerson juridicalPerson) {
        Customer customer = Customer.builder()
                .legalName(juridicalPerson.getBusinessName())
                .documentType(DocumentType.RUC)
                .documentNumber(juridicalPerson.getDocumentNumber())
                // --- fiscal
                .address(juridicalPerson.getAddress())
                .ubigeo(juridicalPerson.getUbigeo())
                .department(juridicalPerson.getDepartment())
                .province(juridicalPerson.getProvince())
                .district(juridicalPerson.getDistrict())
                // --- SUNAT info
                .sunatStatus(juridicalPerson.getStatus())
                .sunatCondition(juridicalPerson.getCondition())
                .streetType(juridicalPerson.getStreetType())
                .streetName(juridicalPerson.getStreetName())
                .zoneCode(juridicalPerson.getZoneCode())
                .zoneType(juridicalPerson.getZoneType())
                .addressNumber(juridicalPerson.getNumber())
                .interior(juridicalPerson.getInterior())
                .lot(juridicalPerson.getLot())
                .apartment(juridicalPerson.getApartment())
                .block(juridicalPerson.getBlock())
                .kilometer(juridicalPerson.getKilometer())
                .retentionAgent(juridicalPerson.isRetentionAgent())
                .goodContributor(juridicalPerson.isGoodContributor())
                .sunatType(juridicalPerson.getType())
                .economicActivity(juridicalPerson.getEconomicActivity())
                .numberOfEmployees(juridicalPerson.getNumberOfEmployees())
                .billingType(juridicalPerson.getBillingType())
                .accountingType(juridicalPerson.getAccountingType())
                .foreignTrade(juridicalPerson.getForeignTrade())
                .build();

        // Direcciones (fiscal + anexos)
        List<CustomerAddress> addresses = new ArrayList<>();

        if (juridicalPerson.getAddress() != null && !juridicalPerson.getAddress().isBlank()) {
            addresses.add(CustomerAddress.builder()
                    .address(juridicalPerson.getAddress())
                    .ubigeo(juridicalPerson.getUbigeo())
                    .department(juridicalPerson.getDepartment())
                    .province(juridicalPerson.getProvince())
                    .district(juridicalPerson.getDistrict())
                    .fiscal(true)
                    .enabled(true)
                    .position(0)
                    .build());
        }

        if (juridicalPerson.getAnnexLocations() != null) {
            int pos = 1;
            for (JuridicalPersonLocal local : juridicalPerson.getAnnexLocations()) {
                if (local == null) continue;
                if (local.getAddress() == null || local.getAddress().isBlank()) continue;

                addresses.add(CustomerAddress.builder()
                        .address(local.getAddress())
                        .ubigeo(local.getUbigeo())
                        .department(local.getDepartment())
                        .province(local.getProvince())
                        .district(local.getDistrict())
                        .fiscal(false)
                        .enabled(true)
                        .position(pos++)
                        .build());
            }
        }

        // Deduplicar por (address + ubigeo + fiscal)
        Set<String> seen = new LinkedHashSet<>();
        List<CustomerAddress> deduped = new ArrayList<>();
        for (CustomerAddress a : addresses) {
            String key = (Objects.toString(a.getAddress(), "").trim() + "|" + Objects.toString(a.getUbigeo(), "").trim() + "|" + a.isFiscal())
                    .toLowerCase();
            if (seen.add(key)) {
                deduped.add(a);
            }
        }

        customer.setAddresses(deduped);
        return customer;
    }
}
