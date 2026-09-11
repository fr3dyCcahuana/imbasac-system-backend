package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CustomerAddressContactValidator {
    public String normalizeRequiredPeruvianMobile(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new InvalidCustomerException("El telefono de la direccion es obligatorio");
        }

        if (trimmed.matches(".*[A-Za-z].*")) {
            throw new InvalidCustomerException("Ingrese un numero celular peruano valido de 9 digitos");
        }

        String digits = trimmed.replaceAll("[\\s()\\-]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }

        if (!digits.matches("\\d+")) {
            throw new InvalidCustomerException("Ingrese un numero celular peruano valido de 9 digitos");
        }

        if (digits.startsWith("51") && digits.length() == 11) {
            digits = digits.substring(2);
        }

        if (digits.length() != 9) {
            throw new InvalidCustomerException("Ingrese un numero celular peruano valido de 9 digitos");
        }

        if (!digits.startsWith("9")) {
            throw new InvalidCustomerException("El numero celular debe comenzar con 9");
        }

        return digits;
    }

    public String normalizeOptionalPeruvianMobile(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : normalizeRequiredPeruvianMobile(trimmed);
    }

    public String normalizeOptionalEmail(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidCustomerException("El correo de la direccion tiene un formato invalido");
        }

        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
