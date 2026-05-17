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
    private Vision vision = new Vision();
    private Campaign campaign = new Campaign();
    private AiAgent aiAgent = new AiAgent();
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

        /** Permite reconocer listas de códigos pegadas en el chat. */
        private boolean batchListEnabled = true;

        /** Cantidad máxima de códigos procesados por mensaje/imagen. */
        private int maxBatchCodesPerMessage = 80;

        /** Cantidad asumida cuando el cliente manda solo códigos sin cantidad. */
        private double defaultBatchQuantity = 1D;

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

    @Getter
    @Setter
    public static class Vision {
        /**
         * Activa lectura automática de imágenes/documentos con IA visual.
         * Si está apagado, el sistema pedirá que el cliente copie los códigos en texto.
         */
        private boolean enabled = false;

        /** API key de OpenAI para extracción visual. Usar variable de entorno en producción. */
        private String openAiApiKey;

        /** Modelo con visión. Déjalo configurable para cambiar sin recompilar. */
        private String openAiModel = "gpt-4o-mini";

        /** Tamaño máximo del archivo descargado desde WhatsApp para enviarlo a visión. */
        private int maxMediaBytes = 5 * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Campaign {
        private boolean enabled = true;
        private boolean requirePolicyConfirmation = true;
        private boolean onlyOptedInByDefault = true;
        private int maxRecipientsPerCampaign = 100;
        private long delayMillisBetweenMessages = 1000;
    }

    @Getter
    @Setter
    public static class AiAgent {
        /**
         * Activa el servicio Python/FastAPI como cerebro conversacional.
         * Si falla, Spring Boot conserva el flujo deterministico actual.
         */
        private boolean enabled = false;

        /** Base URL del servicio imbasac-ai-agent. */
        private String baseUrl = "http://localhost:8010";

        /** API key interna que el servicio IA espera en X-Agent-Api-Key. */
        private String apiKey = "dev-secret";

        /** Endpoint de planeamiento dentro del servicio IA. */
        private String planPath = "/api/v1/agent/plan";

        private int connectTimeoutSeconds = 2;
        private int readTimeoutSeconds = 8;

        public int safeConnectTimeoutSeconds() {
            return connectTimeoutSeconds <= 0 ? 2 : Math.min(connectTimeoutSeconds, 30);
        }

        public int safeReadTimeoutSeconds() {
            return readTimeoutSeconds <= 0 ? 8 : Math.min(readTimeoutSeconds, 60);
        }
    }
}
