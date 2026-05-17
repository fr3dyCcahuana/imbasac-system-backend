package com.paulfernandosr.possystembackend.campaign.domain.port.output;

import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaign;
import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaignRecipient;
import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaignRecipientMode;
import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaignStatus;
import com.paulfernandosr.possystembackend.whatsapp.campaign.domain.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WhatsAppCampaignRepository {
    WhatsAppCampaign saveCampaign(WhatsAppCampaign campaign);
    Optional<WhatsAppCampaign> findCampaignById(Long id);
    List<WhatsAppCampaign> listCampaigns(int page, int size);
    long countCampaigns();
    void updateStatus(Long campaignId, WhatsAppCampaignStatus status, String errorMessage);
    void markStarted(Long campaignId);
    void markCompleted(Long campaignId, WhatsAppCampaignStatus status, int sent, int failed, int skipped, String errorMessage);

    List<WhatsAppCampaignRecipient> previewRecipients(WhatsAppCampaignRecipientMode mode,
                                                      boolean onlyOptedIn,
                                                      Collection<Long> contactIds,
                                                      Collection<String> waIds,
                                                      int limit);

    void replaceRecipients(Long campaignId, List<WhatsAppCampaignRecipient> recipients);
    List<WhatsAppCampaignRecipient> findPendingRecipients(Long campaignId);
    List<WhatsAppCampaignRecipient> findRecipients(Long campaignId, int page, int size);
    void markRecipientSent(Long recipientId, String waMessageId);
    void markRecipientFailed(Long recipientId, String errorMessage);
    void markRecipientSkipped(Long recipientId, String reason);
}
