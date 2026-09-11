package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.customer.domain.GeolocationSource;
import com.paulfernandosr.possystembackend.customer.domain.GeolocationStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public final class CustomerMapDtos {
    private CustomerMapDtos() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerMapConfigResponse {
        private String googleMapsApiKey;
        private String googleMapsMapId;
        private double[] bounds;
        private double[] center;
        private double minZoom;
        private double maxZoom;
        private double urbanZoom;
        private boolean geocoderEnabled;
        private boolean batchEnabled;
        private boolean autoJobEnabled;
        private int autoJobBatchSize;
        private String autoJobCron;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAddressMarkerResponse {
        private Long addressId;
        private Long customerId;
        private String customerName;
        private String documentType;
        private String documentNumber;
        private Boolean mainAddress;
        private String addressText;
        private String phone;
        private String districtName;
        private String provinceName;
        private String departmentName;
        private String ubigeo;
        private String responsibleName;
        private Long responsibleUserId;
        private Double latitude;
        private Double longitude;
        private GeolocationStatus geolocationStatus;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAddressGeolocationDetailResponse {
        private Long addressId;
        private Long customerId;
        private String customerName;
        private String documentType;
        private String documentNumber;
        private Boolean mainAddress;
        private String addressText;
        private String phone;
        private String email;
        private String departmentName;
        private String provinceName;
        private String districtName;
        private String ubigeo;
        private String responsibleName;
        private Long responsibleUserId;
        private Double latitude;
        private Double longitude;
        private GeolocationStatus geolocationStatus;
        private GeolocationSource geolocationSource;
        private Double geolocationAccuracyMeters;
        private LocalDateTime geolocatedAt;
        private String geolocatedByName;
        private LocalDateTime geolocationVerifiedAt;
        private String geolocationVerifiedByName;
        private String googleMapsDirectionsUrl;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerMapSummaryResponse {
        private long totalAddresses;
        private long withCoordinates;
        private long verified;
        private long approximate;
        private long pending;
        private long needsReview;
        private long notFound;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAddressGeolocationUpdateRequest {
        private Double latitude;
        private Double longitude;
        private Double accuracyMeters;
        private GeolocationSource source;
        private LocalDateTime expectedUpdatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeocodingSuggestionResponse {
        private boolean configured;
        private boolean accepted;
        private String message;
        private Double latitude;
        private Double longitude;
        private Double accuracyMeters;
        private String interpretedAddress;
        private String precision;
        private Boolean partialMatch;
        private Boolean withinAdministrativeArea;
        private String rejectionReason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationBatchResponse {
        private String jobId;
        private String status;
        private String message;
        private int requested;
        private int queued;
        private int processed;
        private int saved;
        private int rejected;
        private int failed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationBatchRequest {
        private List<Long> addressIds;
        private Integer limit;
    }
}
