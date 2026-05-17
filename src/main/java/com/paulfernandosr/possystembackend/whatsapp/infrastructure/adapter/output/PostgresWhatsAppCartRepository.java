package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppCartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppCartRepository implements WhatsAppCartRepository {
    private final JdbcClient jdbcClient;

    private final RowMapper<WhatsAppCart> cartMapper = (rs, rowNum) -> WhatsAppCart.builder()
            .id(rs.getLong("id"))
            .conversationId(rs.getLong("conversation_id"))
            .status(WhatsAppEnums.CartStatus.valueOf(rs.getString("status")))
            .currentProductSuggestionId(rs.getObject("current_product_suggestion_id", Long.class))
            .customerDocumentType(rs.getString("customer_document_type"))
            .customerDocumentNumber(rs.getString("customer_document_number"))
            .customerName(rs.getString("customer_name"))
            .proformaId(rs.getObject("proforma_id", Long.class))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .build();

    private final RowMapper<WhatsAppCartItem> itemMapper = (rs, rowNum) -> WhatsAppCartItem.builder()
            .id(rs.getLong("id"))
            .cartId(rs.getLong("cart_id"))
            .productId(rs.getLong("product_id"))
            .productCode(rs.getString("product_code"))
            .productName(rs.getString("product_name"))
            .quantity(rs.getBigDecimal("quantity"))
            .unitPrice(rs.getBigDecimal("unit_price"))
            .priceList(rs.getString("price_list"))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .build();

    @Override
    public WhatsAppCart findOrCreateOpenCart(Long conversationId) {
        Optional<WhatsAppCart> existing = findOpenByConversationId(conversationId);
        if (existing.isPresent()) return existing.get();
        return jdbcClient.sql("""
                INSERT INTO whatsapp_carts(conversation_id, status)
                VALUES (:conversationId, 'OPEN')
                RETURNING *
                """)
                .param("conversationId", conversationId)
                .query(cartMapper)
                .single();
    }

    @Override
    public WhatsAppCart findOrCreateOpenCartForProforma(Long conversationId, Long proformaId) {
        if (proformaId == null) {
            return findOrCreateOpenCart(conversationId);
        }

        Optional<WhatsAppCart> existing = jdbcClient.sql("""
                SELECT * FROM whatsapp_carts
                WHERE conversation_id = :conversationId
                  AND proforma_id = :proformaId
                  AND status IN ('OPEN', 'WAITING_CUSTOMER', 'READY_TO_PROFORMA')
                ORDER BY updated_at DESC
                LIMIT 1
                """)
                .param("conversationId", conversationId)
                .param("proformaId", proformaId)
                .query(cartMapper)
                .optional();
        if (existing.isPresent()) return existing.get();

        return jdbcClient.sql("""
                INSERT INTO whatsapp_carts(conversation_id, proforma_id, status)
                VALUES (:conversationId, :proformaId, 'OPEN')
                RETURNING *
                """)
                .param("conversationId", conversationId)
                .param("proformaId", proformaId)
                .query(cartMapper)
                .single();
    }

    @Override
    public Optional<WhatsAppCart> findOpenByConversationId(Long conversationId) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_carts
                WHERE conversation_id = :conversationId
                  AND status IN ('OPEN', 'WAITING_CUSTOMER', 'READY_TO_PROFORMA')
                ORDER BY updated_at DESC
                LIMIT 1
                """)
                .param("conversationId", conversationId)
                .query(cartMapper)
                .optional();
    }

    @Override
    public void setCurrentProductSuggestion(Long cartId, Long suggestionId) {
        jdbcClient.sql("""
                UPDATE whatsapp_carts
                SET current_product_suggestion_id = :suggestionId, updated_at = now()
                WHERE id = :cartId
                """)
                .param("suggestionId", suggestionId)
                .param("cartId", cartId)
                .update();
    }

    @Override
    public void addItem(WhatsAppCartItem item) {
        jdbcClient.sql("""
                INSERT INTO whatsapp_cart_items(cart_id, product_id, product_code, product_name, quantity, unit_price, price_list)
                VALUES (:cartId, :productId, :productCode, :productName, :quantity, :unitPrice, :priceList)
                """)
                .param("cartId", item.getCartId())
                .param("productId", item.getProductId())
                .param("productCode", item.getProductCode())
                .param("productName", item.getProductName())
                .param("quantity", item.getQuantity())
                .param("unitPrice", item.getUnitPrice())
                .param("priceList", item.getPriceList())
                .update();
        jdbcClient.sql("UPDATE whatsapp_carts SET updated_at = now() WHERE id = :cartId")
                .param("cartId", item.getCartId())
                .update();
    }

    @Override
    public List<WhatsAppCartItem> findItems(Long cartId) {
        return jdbcClient.sql("SELECT * FROM whatsapp_cart_items WHERE cart_id = :cartId ORDER BY id ASC")
                .param("cartId", cartId)
                .query(itemMapper)
                .list();
    }

    @Override
    public void setCustomerDocument(Long cartId, String documentType, String documentNumber) {
        jdbcClient.sql("""
                UPDATE whatsapp_carts
                SET customer_document_type = :documentType,
                    customer_document_number = :documentNumber,
                    status = 'READY_TO_PROFORMA',
                    updated_at = now()
                WHERE id = :cartId
                """)
                .param("documentType", documentType)
                .param("documentNumber", documentNumber)
                .param("cartId", cartId)
                .update();
    }


    @Override
    public void setCustomerName(Long cartId, String customerName) {
        jdbcClient.sql("""
                UPDATE whatsapp_carts
                SET customer_name = :customerName,
                    status = 'READY_TO_PROFORMA',
                    updated_at = now()
                WHERE id = :cartId
                """)
                .param("customerName", customerName == null || customerName.isBlank() ? null : customerName.trim())
                .param("cartId", cartId)
                .update();
    }

    @Override
    public void updateStatus(Long cartId, WhatsAppEnums.CartStatus status) {
        jdbcClient.sql("UPDATE whatsapp_carts SET status = :status, updated_at = now() WHERE id = :cartId")
                .param("status", status.name())
                .param("cartId", cartId)
                .update();
    }

    @Override
    public void linkProforma(Long cartId, Long proformaId) {
        jdbcClient.sql("""
                UPDATE whatsapp_carts
                SET proforma_id = :proformaId, status = 'PROFORMA_CREATED', updated_at = now()
                WHERE id = :cartId
                """)
                .param("proformaId", proformaId)
                .param("cartId", cartId)
                .update();
    }

    @Override
    public void cancelOpenCarts(Long conversationId) {
        jdbcClient.sql("""
                UPDATE whatsapp_carts
                SET status = 'CANCELLED', updated_at = now()
                WHERE conversation_id = :conversationId
                  AND status IN ('OPEN', 'WAITING_CUSTOMER', 'READY_TO_PROFORMA')
                """)
                .param("conversationId", conversationId)
                .update();
    }
}
