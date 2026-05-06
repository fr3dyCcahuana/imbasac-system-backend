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
    /**
     * En la tabla actual varios campos de customers/customer_address son varchar(160).
     * La ficha SUNAT puede devolver textos largos, sobre todo actividad económica y dirección.
     * Por eso se normaliza y limita antes de persistir para no romper el registro del cliente.
     */
    private static final int DB_TEXT_LIMIT = 160;

    public static Customer toCustomer(NaturalPerson naturalPerson) {
        return Customer.builder()
                .legalName(dbText(buildLegalName(
                        naturalPerson.getGivenNames(),
                        naturalPerson.getLastName(),
                        naturalPerson.getSecondLastName())))
                .documentType(DocumentType.DNI)
                .documentNumber(dbText(naturalPerson.getDocumentNumber()))
                .givenNames(dbText(naturalPerson.getGivenNames()))
                .lastName(dbText(naturalPerson.getLastName()))
                .secondLastName(dbText(naturalPerson.getSecondLastName()))
                .build();
    }

    public static Customer toCustomer(JuridicalPerson juridicalPerson) {
        String fiscalAddress = dbText(juridicalPerson.getAddress());

        Customer customer = Customer.builder()
                .legalName(dbText(juridicalPerson.getBusinessName()))
                .documentType(DocumentType.RUC)
                .documentNumber(dbText(juridicalPerson.getDocumentNumber()))
                // --- fiscal
                .address(fiscalAddress)
                .ubigeo(fiscalAddress == null ? null : dbText(juridicalPerson.getUbigeo()))
                .department(fiscalAddress == null ? null : dbText(juridicalPerson.getDepartment()))
                .province(fiscalAddress == null ? null : dbText(juridicalPerson.getProvince()))
                .district(fiscalAddress == null ? null : dbText(juridicalPerson.getDistrict()))
                // --- SUNAT info mínima útil
                .sunatStatus(dbText(juridicalPerson.getStatus()))
                .sunatCondition(dbText(juridicalPerson.getCondition()))
                .streetType(fiscalAddress == null ? null : dbText(juridicalPerson.getStreetType()))
                .streetName(fiscalAddress == null ? null : dbText(juridicalPerson.getStreetName()))
                .zoneCode(fiscalAddress == null ? null : dbText(juridicalPerson.getZoneCode()))
                .zoneType(fiscalAddress == null ? null : dbText(juridicalPerson.getZoneType()))
                .addressNumber(fiscalAddress == null ? null : dbText(juridicalPerson.getNumber()))
                .interior(fiscalAddress == null ? null : dbText(juridicalPerson.getInterior()))
                .lot(fiscalAddress == null ? null : dbText(juridicalPerson.getLot()))
                .apartment(fiscalAddress == null ? null : dbText(juridicalPerson.getApartment()))
                .block(fiscalAddress == null ? null : dbText(juridicalPerson.getBlock()))
                .kilometer(fiscalAddress == null ? null : dbText(juridicalPerson.getKilometer()))
                .retentionAgent(juridicalPerson.isRetentionAgent())
                .goodContributor(juridicalPerson.isGoodContributor())
                .sunatType(dbText(juridicalPerson.getType()))
                .economicActivity(compactEconomicActivity(juridicalPerson.getEconomicActivity()))
                .numberOfEmployees(dbText(juridicalPerson.getNumberOfEmployees()))
                .billingType(dbText(juridicalPerson.getBillingType()))
                .accountingType(dbText(juridicalPerson.getAccountingType()))
                .foreignTrade(dbText(juridicalPerson.getForeignTrade()))
                .build();

        // Direcciones (fiscal + anexos)
        List<CustomerAddress> addresses = new ArrayList<>();

        if (fiscalAddress != null) {
            addresses.add(CustomerAddress.builder()
                    .address(fiscalAddress)
                    .ubigeo(dbText(juridicalPerson.getUbigeo()))
                    .department(dbText(juridicalPerson.getDepartment()))
                    .province(dbText(juridicalPerson.getProvince()))
                    .district(dbText(juridicalPerson.getDistrict()))
                    .fiscal(true)
                    .enabled(true)
                    .position(0)
                    .build());
        }

        if (juridicalPerson.getAnnexLocations() != null) {
            int pos = 1;
            for (JuridicalPersonLocal local : juridicalPerson.getAnnexLocations()) {
                if (local == null) continue;

                String annexAddress = dbText(local.getAddress());
                if (annexAddress == null) continue;

                addresses.add(CustomerAddress.builder()
                        .address(annexAddress)
                        .ubigeo(dbText(local.getUbigeo()))
                        .department(dbText(local.getDepartment()))
                        .province(dbText(local.getProvince()))
                        .district(dbText(local.getDistrict()))
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

    private static String compactEconomicActivity(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }

        // SUNAT puede devolver Principal + Secundaria 1 + Secundaria 2 en una sola cadena.
        // Para customers.economic_activity guardamos solo la actividad principal.
        normalized = cutBefore(normalized, " Secundaria ");
        normalized = cutBefore(normalized, " Secundaria 1 ");
        normalized = cutBefore(normalized, " Secundaria 2 ");
        normalized = cutBefore(normalized, " Comprobantes de Pago ");
        normalized = cutBefore(normalized, " Sistema de Emisión ");
        normalized = cutBefore(normalized, " Sistema de Emision ");

        return truncate(normalized, DB_TEXT_LIMIT);
    }

    private static String cutBefore(String value, String marker) {
        int index = value.toUpperCase().indexOf(marker.toUpperCase());
        return index > 0 ? value.substring(0, index).trim() : value;
    }

    private static String dbText(String value) {
        return truncate(normalizeNullable(value), DB_TEXT_LIMIT);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace(' ', ' ')
                .replaceAll("\s+", " ")
                .trim();

        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
