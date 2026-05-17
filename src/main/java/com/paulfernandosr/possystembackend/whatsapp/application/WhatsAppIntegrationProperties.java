package com.paulfernandosr.possystembackend.whatsapp.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppIntegrationProperties {
    private String apiVersion = "v25.0";
    private String phoneNumberId;
    private String accessToken;
    private String verifyToken;
    private boolean autoReplyEnabled = true;
    private int defaultPageSize = 20;
    private Sales sales = new Sales();

    @Getter
    @Setter
    public static class Sales {
        /**
         * Lista de precio que usará el asistente al responder por WhatsApp.
         * Valores permitidos: A, B, C, D.
         */
        private String defaultPriceList = "A";

        /** Muestra stock disponible en las respuestas automáticas. */
        private boolean showStock = true;

        /** Cantidad máxima de productos que el bot devuelve en un mensaje. */
        private int maxProductsResponse = 5;

        /** Permite activar/desactivar el flujo futuro de generación de proformas desde WhatsApp. */
        private boolean allowProformaGeneration = true;

        /** Si true, el bot solo lista productos con stock disponible. */
        private boolean onlyShowProductsWithStock = true;

        /** Stock mínimo para mostrar como disponible. */
        private double minimumStockToShow = 1D;

        /** Mensaje de cierre cuando el bot encuentra productos. */
        private String productSearchFooter = "Responde con el número del producto para continuar con la proforma.";

        public String normalizedDefaultPriceList() {
            if (defaultPriceList == null || defaultPriceList.isBlank()) {
                return "A";
            }
            String normalized = defaultPriceList.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "A", "B", "C", "D" -> normalized;
                default -> "A";
            };
        }

        public int safeMaxProductsResponse() {
            if (maxProductsResponse <= 0) return 10;
            return Math.min(maxProductsResponse, 10);
        }
    }
}
