package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.infrastructure.sunat.SunatCodeInferer;
import com.paulfernandosr.possystembackend.common.infrastructure.sunat.SunatProductCodeValidator;
import com.paulfernandosr.possystembackend.product.domain.Product;

final class ProductSunatProductCodeResolver {

    private static final String MOTORCYCLE_SUNAT_CODE = "25101801";

    private ProductSunatProductCodeResolver() {
    }

    static String resolveForCreate(Product product) {
        String configured = normalizeConfigured(product != null ? product.getSunatProductCode() : null);
        return configured != null ? configured : inferOptional(product);
    }

    static String resolveForUpdate(Product incoming, Product existing) {
        String registered = normalizeRegistered(existing != null ? existing.getSunatProductCode() : null);
        if (registered != null) {
            return registered;
        }

        String configured = normalizeConfigured(incoming != null ? incoming.getSunatProductCode() : null);
        return configured != null ? configured : inferOptional(incoming);
    }

    private static String normalizeConfigured(String code) {
        return SunatProductCodeValidator.normalizeOptional(code, "Codigo Producto SUNAT del producto");
    }

    private static String normalizeRegistered(String code) {
        try {
            return normalizeConfigured(code);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String inferOptional(Product product) {
        if (product == null) {
            return null;
        }

        try {
            if (isMotorcycle(product.getCategory())) {
                return SunatProductCodeValidator.requireValid(MOTORCYCLE_SUNAT_CODE, "Codigo Producto SUNAT inferido para motocicleta");
            }
            return SunatCodeInferer.infer(product.getName(), product.getCategory());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isMotorcycle(String category) {
        String value = category == null ? "" : category.trim().toUpperCase();
        return value.contains("MOTOCIC") || "MOTO".equals(value) || "MOTOCICLETA".equals(value);
    }
}
