package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.sunat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SunatUbigeoResolver {
    private final JdbcClient jdbcClient;

    private volatile List<CatalogLocation> locations;
    private volatile List<CatalogDepartment> departments;

    public Optional<CatalogLocation> resolve(String department, String province, String district) {
        ensureLoaded();

        String normalizedDepartment = normalizeKey(department);
        String normalizedProvince = normalizeKey(province);
        String normalizedDistrict = normalizeKey(district);

        if (normalizedProvince == null || normalizedDistrict == null) {
            return Optional.empty();
        }

        return locations.stream()
                .filter(location -> normalizedDepartment == null
                        || location.getDepartmentKey().equals(normalizedDepartment))
                .filter(location -> location.getProvinceKey().equals(normalizedProvince))
                .filter(location -> location.getDistrictKey().equals(normalizedDistrict))
                .findFirst();
    }

    public Optional<CatalogLocation> resolveByProvinceAndDistrict(String province, String district) {
        return resolve(null, province, district);
    }

    public Optional<CatalogDepartment> findDepartmentSuffix(String value) {
        ensureLoaded();

        String normalizedValue = normalizeKey(value);
        if (normalizedValue == null) {
            return Optional.empty();
        }

        return departments.stream()
                .filter(department -> normalizedValue.equals(department.getKey())
                        || normalizedValue.endsWith(" " + department.getKey()))
                .findFirst();
    }

    public boolean isDepartmentName(String value) {
        ensureLoaded();

        String normalizedValue = normalizeKey(value);
        if (normalizedValue == null) {
            return false;
        }

        return departments.stream().anyMatch(department -> department.getKey().equals(normalizedValue));
    }

    private void ensureLoaded() {
        if (locations != null && departments != null) {
            return;
        }

        synchronized (this) {
            if (locations != null && departments != null) {
                return;
            }

            locations = loadLocations();
            departments = loadDepartments();
        }
    }

    private List<CatalogLocation> loadLocations() {
        String sql = """
                SELECT
                    dep.code AS department_code,
                    dep.name AS department_name,
                    p.code AS province_code,
                    p.name AS province_name,
                    d.code AS district_code,
                    d.name AS district_name
                FROM district_catalog d
                JOIN province_catalog p
                  ON p.department_code = d.department_code
                 AND p.code = d.province_code
                JOIN department_catalog dep
                  ON dep.code = d.department_code
                """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> CatalogLocation.builder()
                        .departmentCode(rs.getString("department_code"))
                        .departmentName(rs.getString("department_name"))
                        .departmentKey(normalizeKey(rs.getString("department_name")))
                        .provinceCode(rs.getString("province_code"))
                        .provinceName(rs.getString("province_name"))
                        .provinceKey(normalizeKey(rs.getString("province_name")))
                        .districtCode(rs.getString("district_code"))
                        .districtName(rs.getString("district_name"))
                        .districtKey(normalizeKey(rs.getString("district_name")))
                        .build())
                .list();
    }

    private List<CatalogDepartment> loadDepartments() {
        String sql = """
                SELECT code, name
                FROM department_catalog
                """;

        List<CatalogDepartment> list = jdbcClient.sql(sql)
                .query((rs, rowNum) -> CatalogDepartment.builder()
                        .code(rs.getString("code"))
                        .name(rs.getString("name"))
                        .key(normalizeKey(rs.getString("name")))
                        .build())
                .list();

        List<CatalogDepartment> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt((CatalogDepartment d) -> d.getKey().length()).reversed());
        return sorted;
    }

    static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace('\u00A0', ' ').trim();
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }

        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9 ]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();

        return normalized.isBlank() ? null : normalized;
    }

    @Getter
    @lombok.Builder
    public static class CatalogLocation {
        private String departmentCode;
        private String departmentName;
        private String departmentKey;
        private String provinceCode;
        private String provinceName;
        private String provinceKey;
        private String districtCode;
        private String districtName;
        private String districtKey;
    }

    @Getter
    @lombok.Builder
    public static class CatalogDepartment {
        private String code;
        private String name;
        private String key;
    }
}
