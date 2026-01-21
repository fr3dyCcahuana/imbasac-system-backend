package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;

import java.math.BigDecimal;

/**
 * Reglas de negocio para la ficha técnica vehicular.
 */
public final class ProductVehicleSpecsRules {

    private ProductVehicleSpecsRules() {
    }

    public static final String CATEGORY_MOTOR = "MOTOR";
    public static final String CATEGORY_MOTOCICLETAS = "MOTOCICLETAS";

    public static final String TYPE_MOTOR = "MOTOR";
    public static final String TYPE_MOTOCICLETA = "MOTOCICLETA";

    public static boolean isVehicleCategory(String category) {
        String cat = normalize(category);
        return CATEGORY_MOTOR.equals(cat) || CATEGORY_MOTOCICLETAS.equals(cat);
    }

    public static String deriveVehicleTypeFromCategory(String category) {
        String cat = normalize(category);
        if (CATEGORY_MOTOR.equals(cat)) {
            return TYPE_MOTOR;
        }
        if (CATEGORY_MOTOCICLETAS.equals(cat)) {
            return TYPE_MOTOCICLETA;
        }
        return null;
    }

    public static void validateRequired(String category, ProductVehicleSpecs specs) {
        String vehicleType = deriveVehicleTypeFromCategory(category);
        if (vehicleType == null) {
            // No aplica.
            return;
        }

        // VehicleType derivado siempre manda
        specs.setVehicleType(vehicleType);

        // Comunes
        requireText(specs.getBrand(), "brand");
        requireText(specs.getModel(), "model");
        requireText(specs.getBodywork(), "bodywork");
        requirePositive(specs.getEngineCapacity(), "engineCapacity");
        requireText(specs.getFuel(), "fuel");
        requirePositiveInt(specs.getCylinders(), "cylinders");
        requireNonNegative(specs.getNetWeight(), "netWeight");
        requireNonNegative(specs.getPayload(), "payload");
        requireNonNegative(specs.getGrossWeight(), "grossWeight");

        if (TYPE_MOTOCICLETA.equals(vehicleType)) {
            // Adicionales obligatorios
            requireText(specs.getVehicleClass(), "vehicleClass");
            requirePositive(specs.getEnginePower(), "enginePower");
            requireText(specs.getRollingForm(), "rollingForm");

            requirePositiveInt(specs.getSeats(), "seats");
            requirePositiveInt(specs.getPassengers(), "passengers");
            requirePositiveInt(specs.getAxles(), "axles");
            requirePositiveInt(specs.getWheels(), "wheels");

            requirePositive(specs.getLength(), "length");
            requirePositive(specs.getWidth(), "width");
            requirePositive(specs.getHeight(), "height");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidProductException("El campo es obligatorio: " + field);
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidProductException("El campo es obligatorio: " + field);
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException("El campo debe ser mayor a 0: " + field);
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidProductException("El campo es obligatorio: " + field);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidProductException("El campo no puede ser negativo: " + field);
        }
    }

    private static void requirePositiveInt(Integer value, String field) {
        if (value == null) {
            throw new InvalidProductException("El campo es obligatorio: " + field);
        }
        if (value <= 0) {
            throw new InvalidProductException("El campo debe ser mayor a 0: " + field);
        }
    }

    private static String normalize(String v) {
        return v == null ? null : v.trim().toUpperCase();
    }
}
