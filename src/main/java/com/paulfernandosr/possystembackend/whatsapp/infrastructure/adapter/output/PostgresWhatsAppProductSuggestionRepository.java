package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppProductSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppProductSuggestionRepository implements WhatsAppProductSuggestionRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    private final RowMapper<WhatsAppProductSuggestion> mapper = (rs, rowNum) -> WhatsAppProductSuggestion.builder()
            .id(rs.getLong("id"))
            .conversationId(rs.getLong("conversation_id"))
            .productId(rs.getLong("product_id"))
            .position(rs.getInt("position"))
            .productCode(rs.getString("product_code"))
            .productName(rs.getString("product_name"))
            .unitPrice(rs.getBigDecimal("unit_price"))
            .stockQuantity(rs.getBigDecimal("stock_quantity"))
            .priceList(rs.getString("price_list"))
            .rawSnapshot(rs.getString("raw_snapshot"))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .build();

    @Override
    public void replaceSuggestions(Long conversationId, List<WhatsAppProductSearchResult> products, String rawSearchText) {
        jdbcClient.sql("DELETE FROM whatsapp_product_suggestions WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        int position = 1;
        for (WhatsAppProductSearchResult product : products) {
            jdbcClient.sql("""
                    INSERT INTO whatsapp_product_suggestions(
                        conversation_id, product_id, position, product_code, product_name,
                        unit_price, stock_quantity, price_list, raw_snapshot
                    ) VALUES (
                        :conversationId, :productId, :position, :productCode, :productName,
                        :unitPrice, :stockQuantity, :priceList, CAST(:rawSnapshot AS jsonb)
                    )
                    """)
                    .param("conversationId", conversationId)
                    .param("productId", product.getProductId())
                    .param("position", position++)
                    .param("productCode", product.getSku())
                    .param("productName", product.getName())
                    .param("unitPrice", product.getSelectedPrice())
                    .param("stockQuantity", product.getStockQuantity())
                    .param("priceList", product.getSelectedPriceList())
                    .param("rawSnapshot", snapshot(product, rawSearchText))
                    .update();
        }
    }

    @Override
    public void replaceBatchSuggestions(Long conversationId, List<WhatsAppBatchQuoteResult.AddedItem> items, String rawSearchText) {
        jdbcClient.sql("DELETE FROM whatsapp_product_suggestions WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        int position = 1;
        for (WhatsAppBatchQuoteResult.AddedItem item : items) {
            jdbcClient.sql("""
                    INSERT INTO whatsapp_product_suggestions(
                        conversation_id, product_id, position, product_code, product_name,
                        unit_price, stock_quantity, price_list, raw_search_text, raw_snapshot
                    ) VALUES (
                        :conversationId, :productId, :position, :productCode, :productName,
                        :unitPrice, :stockQuantity, :priceList, :rawSearchText, CAST(:rawSnapshot AS jsonb)
                    )
                    """)
                    .param("conversationId", conversationId)
                    .param("productId", item.getProductId())
                    .param("position", position++)
                    .param("productCode", item.getCode())
                    .param("productName", item.getName())
                    .param("unitPrice", item.getUnitPrice())
                    .param("stockQuantity", item.getStock())
                    .param("priceList", item.getPriceList())
                    .param("rawSearchText", rawSearchText == null ? "" : rawSearchText)
                    .param("rawSnapshot", batchSnapshot(item, rawSearchText))
                    .update();
        }
    }

    @Override
    public Optional<WhatsAppProductSuggestion> findLatestByPosition(Long conversationId, int position) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_product_suggestions
                WHERE conversation_id = :conversationId AND position = :position
                ORDER BY created_at DESC
                LIMIT 1
                """)
                .param("conversationId", conversationId)
                .param("position", position)
                .query(mapper)
                .optional();
    }

    @Override
    public Optional<WhatsAppProductSuggestion> findById(Long id) {
        return jdbcClient.sql("SELECT * FROM whatsapp_product_suggestions WHERE id = :id")
                .param("id", id)
                .query(mapper)
                .optional();
    }

    @Override
    public List<WhatsAppProductSuggestion> findLatest(Long conversationId, int limit) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_product_suggestions
                WHERE conversation_id = :conversationId
                ORDER BY position ASC
                LIMIT :limit
                """)
                .param("conversationId", conversationId)
                .param("limit", limit)
                .query(mapper)
                .list();
    }

    private String batchSnapshot(WhatsAppBatchQuoteResult.AddedItem item, String rawSearchText) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "searchText", rawSearchText == null ? "" : rawSearchText,
                    "sku", item.getCode() == null ? "" : item.getCode(),
                    "name", item.getName() == null ? "" : item.getName(),
                    "stock", item.getStock() == null ? "" : item.getStock().toPlainString(),
                    "price", item.getUnitPrice() == null ? "" : item.getUnitPrice().toPlainString(),
                    "priceList", item.getPriceList() == null ? "" : item.getPriceList(),
                    "requestedQuantity", item.getQuantity() == null ? "1" : item.getQuantity().toPlainString(),
                    "source", "batch"
            ));
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String snapshot(WhatsAppProductSearchResult product, String rawSearchText) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "searchText", rawSearchText == null ? "" : rawSearchText,
                    "sku", product.getSku() == null ? "" : product.getSku(),
                    "name", product.getName() == null ? "" : product.getName(),
                    "stock", product.getStockQuantity() == null ? "" : product.getStockQuantity().toPlainString(),
                    "price", product.getSelectedPrice() == null ? "" : product.getSelectedPrice().toPlainString(),
                    "priceList", product.getSelectedPriceList() == null ? "" : product.getSelectedPriceList()
            ));
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
