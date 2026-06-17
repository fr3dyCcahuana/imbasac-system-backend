package com.paulfernandosr.possystembackend.product.domain;

import java.util.Map;
import java.util.Set;

public final class ProductExistenceType {
    public static final String DEFAULT_CODE = "01";

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "01", "MERCADERIA",
            "02", "PRODUCTO TERMINADO",
            "03", "MATERIAS PRIMAS Y AUXILIARES - MATERIALES",
            "04", "ENVASES Y EMBALAJES",
            "05", "SUMINISTROS DIVERSOS",
            "99", "OTROS (ESPECIFICAR)"
    );

    private ProductExistenceType() {
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_CODE;
        }
        return code.trim();
    }

    public static boolean isAllowed(String code) {
        return DESCRIPTIONS.containsKey(normalize(code));
    }

    public static String description(String code) {
        return DESCRIPTIONS.getOrDefault(normalize(code), DESCRIPTIONS.get(DEFAULT_CODE));
    }

    public static Set<String> allowedCodes() {
        return DESCRIPTIONS.keySet();
    }
}
