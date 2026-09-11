package com.paulfernandosr.possystembackend.customer.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.customer-map")
public class CustomerMapProperties {
    private MapSettings map = new MapSettings();
    private GeocoderSettings geocoder = new GeocoderSettings();

    @Getter
    @Setter
    public static class MapSettings {
        private String googleMapsApiKey;
        private String googleMapsMapId;
        private double[] bounds = {-81.5, -18.5, -68.5, 0.5};
        private double[] center = {-75.0152, -9.19};
        private double minZoom = 4;
        private double maxZoom = 19;
        private double urbanZoom = 17;
    }

    @Getter
    @Setter
    public static class GeocoderSettings {
        private boolean enabled = false;
        private String provider = "NOMINATIM";
        private String baseUrl;
        private int timeoutMs = 8000;
        private int rateLimitPerMinute = 4;
        private int maxRetries = 1;
        private String country = "PE";
        private String language = "es";
        private String userAgent = "IMBASAC customer map";
        private boolean batchEnabled = false;
        private AutoJobSettings autoJob = new AutoJobSettings();
    }

    @Getter
    @Setter
    public static class AutoJobSettings {
        private boolean enabled = false;
        private String cron = "0 */5 * * * *";
        private int batchSize = 20;
    }
}
