package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;

import java.math.BigDecimal;

/**
 * Reglas de validación para product_vehicle_specs.
 *
 * IMPORTANTE (2026-01): brand y model se movieron a la tabla product.
 * En specs solo quedan los datos técnicos.
 */
public class ProductVehicleSpecsRules {

    private ProductVehicleSpecsRules() {}

    public static boolean isVehicleCategory(String category) {
        if (category == null) return false;
        String c = category.trim().toUpperCase();
        return c.equals("MOTOR") || c.equals("MOTOCICLETAS");
    }

    public static String deriveVehicleType(String category) {
        if (category == null) return null;
        String c = category.trim().toUpperCase();
        if (c.equals("MOTOR")) return "MOTOR";
        if (c.equals("MOTOCICLETAS")) return "MOTOCICLETA";
        return null;
    }

    /**
     * Valida los campos obligatorios de specs según la categoría del producto.
     * También setea vehicleType derivado por category.
     */
    public static void validateRequired(String productCategory, ProductVehicleSpecs specs) {
        if (specs == null) {
            throw new InvalidProductException("vehicleSpecs no puede ser null.");
        }

        String vehicleType = deriveVehicleType(productCategory);
        if (vehicleType == null) {
            throw new InvalidProductException("vehicleSpecs aplica solo para category MOTOR o MOTOCICLETAS.");
        }
        specs.setVehicleType(vehicleType);

        // --------------------
        // Comunes
        // --------------------
        requireText(specs.getBodywork(), "bodywork");
        requireText(specs.getFuel(), "fuel");

        requirePositive(specs.getEngineCapacity(), "engineCapacity");
        requirePositive(specs.getCylinders(), "cylinders");

        requireNonNegative(specs.getNetWeight(), "netWeight");
        requireNonNegative(specs.getPayload(), "payload");
        requireNonNegative(specs.getGrossWeight(), "grossWeight");

        // --------------------
        // Solo MOTOCICLETA
        // --------------------
        if ("MOTOCICLETA".equals(vehicleType)) {
            requireText(specs.getVehicleClass(), "vehicleClass");
            requirePositive(specs.getEnginePower(), "enginePower");
            requireText(specs.getRollingForm(), "rollingForm");

            requirePositive(specs.getSeats(), "seats");
            requirePositive(specs.getPassengers(), "passengers");
            requirePositive(specs.getAxles(), "axles");
            requirePositive(specs.getWheels(), "wheels");

            requirePositive(specs.getLength(), "length");
            requirePositive(specs.getWidth(), "width");
            requirePositive(specs.getHeight(), "height");
        }
    }

    private static void requireText(String val, String field) {
        if (val == null || val.trim().isEmpty()) {
            throw new InvalidProductException(field + " es obligatorio.");
        }
    }

    private static void requirePositive(Integer val, String field) {
        if (val == null || val <= 0) {
            throw new InvalidProductException(field + " debe ser > 0.");
        }
    }

    private static void requirePositive(BigDecimal val, String field) {
        if (val == null || val.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException(field + " debe ser > 0.");
        }
    }

    private static void requireNonNegative(BigDecimal val, String field) {
        if (val == null || val.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidProductException(field + " debe ser >= 0.");
        }
    }
}
