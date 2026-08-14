package com.paulfernandosr.possystembackend.mycustomers.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MyCustomersDtos {
    private MyCustomersDtos() {
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageResponse<T> {
        private List<T> payload;
        private PageMetadata metadata;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PageMetadata {
        private int page;
        private int size;
        private int numberOfElements;
        private long totalElements;
        private int totalPages;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyCustomersSummaryResponse {
        private long assignedCustomers;
        private long withContact;
        private double withContactPercent;
        private long withoutRecentContact;
        private long pendingFollowups;
        private boolean canViewAllPortfolios;
        private Long currentUserId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyCustomerRowResponse {
        private Long customerId;
        private String legalName;
        private String documentType;
        private String documentNumber;
        private String phone;
        private String email;
        private String address;
        private String district;
        private Long responsibleUserId;
        private String responsibleName;
        private String commercialStatus;
        private LocalDateTime lastPurchaseAt;
        private BigDecimal lastPurchaseTotal;
        private String lastPurchaseSource;
        private LocalDateTime lastProformaAt;
        private BigDecimal lastProformaTotal;
        private String lastProformaStatus;
        private LocalDateTime lastContactAt;
        private String lastContactSource;
        private LocalDateTime nextFollowupAt;
        private String nextFollowupType;
        private String nextFollowupStatus;
        private String whatsappStatus;
        private List<TagResponse> tags;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyCustomerDetailResponse {
        private MyCustomerRowResponse customer;
        private List<ActivityResponse> recentActivity;
        private List<FollowupResponse> followups;
        private List<MaterialResponse> materials;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TagResponse {
        private Long id;
        private String name;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FollowupRequest {
        private String followupAt;
        private String type;
        private String note;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FollowupResponse {
        private Long id;
        private Long customerId;
        private Long assignedUserId;
        private String assignedUserName;
        private LocalDateTime followupAt;
        private String type;
        private String note;
        private String status;
        private Long createdBy;
        private String createdByName;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private String completedByName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommercialStatusRequest {
        private String status;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TagRequest {
        private Long tagId;
        private String name;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WhatsAppTextRequest {
        private String message;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendMaterialRequest {
        private String materialType;
        private Long materialId;
        private String message;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendCommercialResponse {
        private Long customerId;
        private Long conversationId;
        private Long activityId;
        private String status;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActivityResponse {
        private Long id;
        private Long customerId;
        private Long userId;
        private String userName;
        private String type;
        private String title;
        private String detail;
        private String relatedType;
        private Long relatedId;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WhatsAppMessageResponse {
        private Long conversationId;
        private Long messageId;
        private String direction;
        private String textBody;
        private String messageType;
        private String currentStatus;
        private LocalDateTime createdAt;
        private LocalDateTime waTimestamp;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OfferHistoryResponse {
        private Long activityId;
        private String type;
        private String title;
        private String detail;
        private String relatedType;
        private Long relatedId;
        private LocalDateTime sentAt;
        private String sentByName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MaterialResponse {
        private String type;
        private Long id;
        private String name;
        private String description;
        private String status;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
    }
}
