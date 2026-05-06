package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.sunat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SunatAddressParser {
    private final SunatUbigeoResolver ubigeoResolver;

    public Optional<ParsedSunatAddress> parse(String sunatAddress) {
        String normalized = normalizeText(sunatAddress);
        if (isBlank(normalized)) {
            return Optional.empty();
        }

        String[] parts = Arrays.stream(normalized.split("\\s+-\\s+"))
                .map(this::normalizeText)
                .filter(part -> !isBlank(part))
                .toArray(String[]::new);

        if (parts.length >= 3 && ubigeoResolver.isDepartmentName(parts[parts.length - 3])) {
            String department = parts[parts.length - 3];
            String province = parts[parts.length - 2];
            String district = parts[parts.length - 1];
            String address = join(parts, 0, parts.length - 3);

            return ubigeoResolver.resolve(department, province, district)
                    .map(location -> build(address, location));
        }

        if (parts.length >= 2) {
            String province = parts[parts.length - 2];
            String district = parts[parts.length - 1];
            String addressWithDepartment = join(parts, 0, parts.length - 2);

            Optional<SunatUbigeoResolver.CatalogLocation> resolved = resolveWithTrailingDepartment(
                    addressWithDepartment,
                    province,
                    district
            );

            if (resolved.isPresent()) {
                SunatUbigeoResolver.CatalogLocation location = resolved.get();
                String address = removeTrailingDepartment(addressWithDepartment, location.getDepartmentName());
                return Optional.of(build(address, location));
            }

            return ubigeoResolver.resolveByProvinceAndDistrict(province, district)
                    .map(location -> build(removeTrailingDepartment(addressWithDepartment, location.getDepartmentName()), location));
        }

        return Optional.of(new ParsedSunatAddress(normalized, null, null, null, null));
    }

    private Optional<SunatUbigeoResolver.CatalogLocation> resolveWithTrailingDepartment(String addressWithDepartment,
                                                                                         String province,
                                                                                         String district) {
        Optional<SunatUbigeoResolver.CatalogDepartment> departmentSuffix = ubigeoResolver.findDepartmentSuffix(addressWithDepartment);
        if (departmentSuffix.isEmpty()) {
            return Optional.empty();
        }

        return ubigeoResolver.resolve(departmentSuffix.get().getName(), province, district);
    }

    private ParsedSunatAddress build(String address, SunatUbigeoResolver.CatalogLocation location) {
        String normalizedAddress = normalizeText(address);
        return new ParsedSunatAddress(
                normalizedAddress,
                location.getDistrictCode(),
                normalizeText(location.getDepartmentName()),
                normalizeText(location.getProvinceName()),
                normalizeText(location.getDistrictName())
        );
    }

    private String removeTrailingDepartment(String value, String department) {
        String normalizedValue = normalizeText(value);
        String normalizedDepartment = normalizeText(department);

        if (isBlank(normalizedValue) || isBlank(normalizedDepartment)) {
            return normalizedValue;
        }

        String valueKey = SunatUbigeoResolver.normalizeKey(normalizedValue);
        String departmentKey = SunatUbigeoResolver.normalizeKey(normalizedDepartment);

        if (valueKey == null || departmentKey == null) {
            return normalizedValue;
        }

        if (!valueKey.equals(departmentKey) && !valueKey.endsWith(" " + departmentKey)) {
            return normalizedValue;
        }

        int departmentWords = normalizedDepartment.split("\\s+").length;
        String[] valueWords = normalizedValue.split("\\s+");

        if (valueWords.length <= departmentWords) {
            return normalizedValue;
        }

        return Arrays.stream(valueWords, 0, valueWords.length - departmentWords)
                .collect(Collectors.joining(" "))
                .trim();
    }

    private String join(String[] values, int startInclusive, int endExclusive) {
        if (values == null || startInclusive >= endExclusive) {
            return null;
        }

        return Arrays.stream(values, startInclusive, endExclusive)
                .map(this::normalizeText)
                .filter(value -> !isBlank(value))
                .collect(Collectors.joining(" - "));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isBlank() || "-".equals(normalized) ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
