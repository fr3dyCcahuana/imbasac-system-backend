package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class WhatsAppCenterDtos {
    private WhatsAppCenterDtos() {
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
    public static class DashboardSummaryResponse {
        private long totalConversations;
        private long openConversations;
        private long quotedConversations;
        private long standbyConversations;
        private long closedSuccessConversations;
        private long closedLostConversations;
        private long pendingHumanConversations;
        private long totalMessagesToday;
        private long inboundMessagesToday;
        private long outboundMessagesToday;
        private long proformasCreatedToday;
        private long accountAlertsActive;
        private long failedMessagesToday;
        private long campaignsRunning;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DailyMetricResponse {
        private LocalDate date;
        private long conversationsCreated;
        private long messagesInbound;
        private long messagesOutbound;
        private long proformasCreated;
        private long closedSuccess;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConversationSummaryResponse {
        private Long conversationId;
        private Long contactId;
        private String waId;
        private String phoneNumber;
        private String profileName;
        private String currentStatus;
        private String lastEventType;
        private OffsetDateTime lastEventAt;
        private String lastMessagePreview;
        private OffsetDateTime lastMessageAt;
        private Long proformaId;
        private String proformaSeries;
        private Long proformaNumber;
        private BigDecimal proformaTotal;
        private Long sellerId;
        private String sellerName;
        private OffsetDateTime conversationCreatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConversationDetailResponse {
        private Long conversationId;
        private ContactInfo contact;
        private String currentStatus;
        private String lastEventType;
        private ProformaInfo proforma;
        private SellerInfo seller;
        private OffsetDateTime conversationCreatedAt;
        private OffsetDateTime lastEventAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ContactInfo {
        private Long contactId;
        private String waId;
        private String phoneNumber;
        private String profileName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProformaInfo {
        private Long proformaId;
        private String series;
        private Long number;
        private BigDecimal total;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SellerInfo {
        private Long sellerId;
        private String sellerName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageResponse {
        private Long messageId;
        private Long conversationId;
        private String direction;
        private String waMessageId;
        private String fromWaId;
        private String toWaId;
        private String messageType;
        private String textBody;
        private String mediaId;
        private String mediaUrl;
        private String mediaMimeType;
        private String mediaFilename;
        private String mediaCaption;
        private String interactiveType;
        private String interactiveId;
        private String interactiveTitle;
        private BigDecimal locationLatitude;
        private BigDecimal locationLongitude;
        private String locationName;
        private String locationAddress;
        private String currentStatus;
        private OffsetDateTime waTimestamp;
        private OffsetDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConversationEventResponse {
        private Long eventId;
        private Long conversationId;
        private String eventType;
        private String status;
        private Long proformaId;
        private String proformaSeries;
        private Long proformaNumber;
        private BigDecimal proformaTotal;
        private Long sellerId;
        private String sellerName;
        private String notes;
        private JsonNode metadata;
        private OffsetDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ManualModeRequest {
        private Boolean enabled;
        private Long advisorId;
        private String advisorName;
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendManualMessageRequest {
        private String body;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageStatusEventResponse {
        private Long id;
        private String waMessageId;
        private String recipientWaId;
        private String status;
        private String metaConversationId;
        private String pricingCategory;
        private String pricingModel;
        private Boolean billable;
        private String errorCode;
        private String errorTitle;
        private String errorMessage;
        private OffsetDateTime waTimestamp;
        private OffsetDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AgentRunResponse {
        private Long id;
        private Long conversationId;
        private Long inputMessageId;
        private String intent;
        private String modelName;
        private String modelProvider;
        private BigDecimal confidence;
        private JsonNode promptMetadata;
        private JsonNode resultJson;
        private String errorMessage;
        private OffsetDateTime startedAt;
        private OffsetDateTime finishedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProductSuggestionResponse {
        private Long id;
        private Long conversationId;
        private Long sourceMessageId;
        private String suggestionGroupId;
        private Integer position;
        private String sourceQuery;
        private Long productId;
        private String sku;
        private String productName;
        private JsonNode productSnapshot;
        private OffsetDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuoteDraftEventResponse {
        private Long id;
        private Long conversationId;
        private String eventType;
        private Long productId;
        private String sku;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String customerDocumentType;
        private String customerDocumentNumber;
        private String customerName;
        private String department;
        private String province;
        private String district;
        private String address;
        private Long officialProformaId;
        private String officialProformaSeries;
        private Long officialProformaNumber;
        private JsonNode metadata;
        private OffsetDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TemplateEventResponse {
        private Long id;
        private String wabaId;
        private String eventType;
        private String messageTemplateId;
        private String messageTemplateName;
        private String messageTemplateLanguage;
        private String previousCategory;
        private String newCategory;
        private String templateStatus;
        private String reason;
        private OffsetDateTime receivedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AccountAlertResponse {
        private Long id;
        private String wabaId;
        private String entityType;
        private String entityId;
        private String alertSeverity;
        private String alertStatus;
        private String alertType;
        private String alertDescription;
        private OffsetDateTime receivedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WebhookEventResponse {
        private Long id;
        private String objectType;
        private String wabaId;
        private String entryId;
        private String fieldName;
        private String eventKind;
        private String waMessageId;
        private Boolean signatureValid;
        private OffsetDateTime receivedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WebhookEventDetailResponse {
        private Long id;
        private String objectType;
        private String wabaId;
        private String entryId;
        private String fieldName;
        private String eventKind;
        private String waMessageId;
        private Boolean signatureValid;
        private JsonNode rawPayload;
        private OffsetDateTime receivedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CampaignResponse {
        private Long campaignId;
        private String name;
        private String description;
        private String templateName;
        private String languageCode;
        private String recipientMode;
        private String currentStatus;
        private Integer totalRecipients;
        private Integer sentCount;
        private Integer failedCount;
        private OffsetDateTime createdAt;
        private OffsetDateTime lastEventAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CampaignRecipientResponse {
        private Long id;
        private Long campaignId;
        private Long contactId;
        private String waId;
        private String phoneNumber;
        private String profileName;
        private String status;
        private String waMessageId;
        private String errorMessage;
        private OffsetDateTime sentAt;
        private OffsetDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CampaignEventResponse {
        private Long id;
        private Long campaignId;
        private String eventType;
        private String status;
        private String notes;
        private JsonNode metadata;
        private OffsetDateTime createdAt;
    }
}
