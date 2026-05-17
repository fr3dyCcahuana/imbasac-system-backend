package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.proformav2.domain.port.input.CreateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.UpdateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.UpdateProformaV2Request;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppCartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WhatsAppProformaCreationService {
    private final CreateProformaV2UseCase createProformaV2UseCase;
    private final UpdateProformaV2UseCase updateProformaV2UseCase;
    private final ProformaItemRepository proformaItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProformaV2Response create(CreateProformaV2Request request) {
        return createProformaV2UseCase.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProformaV2Response appendItems(Long proformaId, List<WhatsAppCartItem> newItems) {
        List<UpdateProformaV2Request.Item> finalItems = new ArrayList<>();
        for (ProformaItem item : proformaItemRepository.findByProformaId(proformaId)) {
            finalItems.add(fromExistingItem(item));
        }
        for (WhatsAppCartItem item : newItems) {
            mergeOrAdd(finalItems, fromCartItem(item));
        }

        UpdateProformaV2Request request = UpdateProformaV2Request.builder()
                .items(finalItems)
                .build();
        return updateProformaV2UseCase.update(proformaId, request);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RemoveItemsResult removeItems(Long proformaId, List<Long> productIds, List<String> productCodes) {
        Set<Long> productIdSet = new LinkedHashSet<>();
        if (productIds != null) {
            for (Long productId : productIds) {
                if (productId != null) productIdSet.add(productId);
            }
        }

        Set<String> codeSet = new LinkedHashSet<>();
        if (productCodes != null) {
            for (String code : productCodes) {
                String normalized = normalizeCode(code);
                if (normalized != null) codeSet.add(normalized);
            }
        }

        if (productIdSet.isEmpty() && codeSet.isEmpty()) {
            return new RemoveItemsResult(null, 0, false);
        }

        List<UpdateProformaV2Request.Item> finalItems = new ArrayList<>();
        int removedCount = 0;

        for (ProformaItem item : proformaItemRepository.findByProformaId(proformaId)) {
            boolean removeByProductId = item.getProductId() != null && productIdSet.contains(item.getProductId());
            boolean removeBySku = codeSet.contains(normalizeCode(item.getSku()));

            if (removeByProductId || removeBySku) {
                removedCount++;
                continue;
            }

            finalItems.add(fromExistingItem(item));
        }

        if (removedCount == 0) {
            return new RemoveItemsResult(null, 0, false);
        }

        if (finalItems.isEmpty()) {
            return new RemoveItemsResult(null, removedCount, true);
        }

        UpdateProformaV2Request request = UpdateProformaV2Request.builder()
                .items(finalItems)
                .build();

        return new RemoveItemsResult(updateProformaV2UseCase.update(proformaId, request), removedCount, false);
    }

    private UpdateProformaV2Request.Item fromExistingItem(ProformaItem item) {
        return UpdateProformaV2Request.Item.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .description(item.getDescription())
                .presentation(item.getPresentation())
                .factor(decimalString(item.getFactor()))
                .quantity(decimalString(item.getQuantity()))
                .discountPercent(decimalString(item.getDiscountPercent()))
                .unitPriceOverride(item.getUnitPrice())
                .build();
    }

    private UpdateProformaV2Request.Item fromCartItem(WhatsAppCartItem item) {
        return UpdateProformaV2Request.Item.builder()
                .productId(item.getProductId())
                .description(item.getProductName())
                .quantity(decimalString(item.getQuantity()))
                .unitPriceOverride(item.getUnitPrice())
                .build();
    }

    private void mergeOrAdd(List<UpdateProformaV2Request.Item> finalItems, UpdateProformaV2Request.Item candidate) {
        for (UpdateProformaV2Request.Item existing : finalItems) {
            if (sameMergeKey(existing, candidate)) {
                existing.setQuantity(decimalString(parse(existing.getQuantity()).add(parse(candidate.getQuantity()))));
                return;
            }
        }
        finalItems.add(candidate);
    }

    private boolean sameMergeKey(UpdateProformaV2Request.Item left, UpdateProformaV2Request.Item right) {
        return Objects.equals(left.getProductId(), right.getProductId())
                && compareMoney(left.getUnitPriceOverride(), right.getUnitPriceOverride()) == 0
                && parse(left.getDiscountPercent()).compareTo(BigDecimal.ZERO) == 0;
    }

    private int compareMoney(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) return 0;
        if (left == null || right == null) return -1;
        return left.compareTo(right);
    }

    private BigDecimal parse(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private String decimalString(BigDecimal value) {
        if (value == null) return null;
        return value.stripTrailingZeros().toPlainString();
    }

    private String normalizeCode(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        return normalized.isBlank() ? null : normalized;
    }

    public record RemoveItemsResult(ProformaV2Response proforma, int removedCount, boolean wouldLeaveEmptyProforma) {}

}
