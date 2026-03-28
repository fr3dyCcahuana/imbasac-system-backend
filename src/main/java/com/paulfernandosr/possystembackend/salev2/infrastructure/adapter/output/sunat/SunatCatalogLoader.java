package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

public final class SunatCatalogLoader {
    private static final String CATALOG_FILE = "sunat-product-catalog.json";

    private SunatCatalogLoader() {
    }

    public static SunatCatalogRoot load(ObjectMapper objectMapper) {
        try {
            ClassPathResource resource = new ClassPathResource(CATALOG_FILE);

            if (!resource.exists()) {
                throw new IllegalStateException("No se encontró el archivo " + CATALOG_FILE + " en classpath");
            }

            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();

                if (bytes.length == 0) {
                    throw new IllegalStateException("El archivo " + CATALOG_FILE + " está vacío en classpath");
                }

                ObjectMapper safeMapper = objectMapper.copy()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                return safeMapper.readValue(bytes, SunatCatalogRoot.class);
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo SUNAT desde JSON", e);
        }
    }
}