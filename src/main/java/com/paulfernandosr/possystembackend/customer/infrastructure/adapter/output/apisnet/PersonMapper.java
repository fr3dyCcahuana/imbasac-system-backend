package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class PersonMapper {
    public static Customer toCustomer(NaturalPerson naturalPerson) {
        return Customer.builder()
                .legalName(buildLegalName(
                        naturalPerson.getGivenNames(),
                        naturalPerson.getLastName(),
                        naturalPerson.getSecondLastName()))
                .documentType(DocumentType.DNI)
                .documentNumber(naturalPerson.getDocumentNumber())
                .givenNames(naturalPerson.getGivenNames())
                .lastName(naturalPerson.getLastName())
                .secondLastName(naturalPerson.getSecondLastName())
                .build();
    }

    public static Customer toCustomer(JuridicalPerson juridicalPerson) {
        String fiscalAddress = normalizeNullable(juridicalPerson.getAddress());

        Customer customer = Customer.builder()
                .legalName(normalizeNullable(juridicalPerson.getBusinessName()))
                .documentType(DocumentType.RUC)
                .documentNumber(normalizeNullable(juridicalPerson.getDocumentNumber()))
                // --- fiscal
                .address(fiscalAddress)
                .ubigeo(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getUbigeo()))
                .department(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getDepartment()))
                .province(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getProvince()))
                .district(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getDistrict()))
                // --- SUNAT info
                .sunatStatus(normalizeNullable(juridicalPerson.getStatus()))
                .sunatCondition(normalizeNullable(juridicalPerson.getCondition()))
                .streetType(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getStreetType()))
                .streetName(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getStreetName()))
                .zoneCode(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getZoneCode()))
                .zoneType(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getZoneType()))
                .addressNumber(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getNumber()))
                .interior(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getInterior()))
                .lot(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getLot()))
                .apartment(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getApartment()))
                .block(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getBlock()))
                .kilometer(fiscalAddress == null ? null : normalizeNullable(juridicalPerson.getKilometer()))
                .retentionAgent(juridicalPerson.isRetentionAgent())
                .goodContributor(juridicalPerson.isGoodContributor())
                .sunatType(normalizeNullable(juridicalPerson.getType()))
                .economicActivity(normalizeNullable(juridicalPerson.getEconomicActivity()))
                .numberOfEmployees(normalizeNullable(juridicalPerson.getNumberOfEmployees()))
                .billingType(normalizeNullable(juridicalPerson.getBillingType()))
                .accountingType(normalizeNullable(juridicalPerson.getAccountingType()))
                .foreignTrade(normalizeNullable(juridicalPerson.getForeignTrade()))
                .build();

        // Direcciones (fiscal + anexos)
        List<CustomerAddress> addresses = new ArrayList<>();

        if (fiscalAddress != null) {
            addresses.add(CustomerAddress.builder()
                    .address(fiscalAddress)
                    .ubigeo(normalizeNullable(juridicalPerson.getUbigeo()))
                    .department(normalizeNullable(juridicalPerson.getDepartment()))
                    .province(normalizeNullable(juridicalPerson.getProvince()))
                    .district(normalizeNullable(juridicalPerson.getDistrict()))
                    .fiscal(true)
                    .enabled(true)
                    .position(0)
                    .build());
        }

        if (juridicalPerson.getAnnexLocations() != null) {
            int pos = 1;
            for (JuridicalPersonLocal local : juridicalPerson.getAnnexLocations()) {
                if (local == null) continue;

                String annexAddress = normalizeNullable(local.getAddress());
                if (annexAddress == null) continue;

                addresses.add(CustomerAddress.builder()
                        .address(annexAddress)
                        .ubigeo(normalizeNullable(local.getUbigeo()))
                        .department(normalizeNullable(local.getDepartment()))
                        .province(normalizeNullable(local.getProvince()))
                        .district(normalizeNullable(local.getDistrict()))
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

    private static String buildLegalName(String... parts) {
        return Stream.of(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }

        return normalized;
    }
}
