package com.paulfernandosr.possystembackend.landing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.landing.domain.PromotionSubmission;
import com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto.PromotionSubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PromotionSubmissionService {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public PromotionSubmission create(PromotionSubmissionRequest request) {
        String name = required(request.businessName(), "businessName");
        String title = required(request.title(), "title");
        String code = nextCode();

        Long id = jdbcClient.sql("""
                INSERT INTO landing_promotion_request (
                    code, business_name, contact_name, phone, document_number, type,
                    title, description, image_url, starts_at, ends_at, items_json, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'NEW')
                RETURNING id
                """)
                .params(
                        code,
                        name,
                        required(request.contactName(), "contactName"),
                        required(request.phone(), "phone"),
                        clean(request.documentNumber()),
                        value(request.type(), "OFFER"),
                        title,
                        clean(request.description()),
                        clean(request.imageUrl()),
                        ts(request.startsAt()),
                        ts(request.endsAt()),
                        toJson(request.items())
                )
                .query(Long.class)
                .single();

        return findById(id);
    }

    private PromotionSubmission findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, code, business_name, contact_name, phone, document_number, type,
                       title, description, image_url, starts_at, ends_at, status, created_at
                FROM landing_promotion_request
                WHERE id = ?
                """)
                .param(id)
                .query((rs, rowNum) -> new PromotionSubmission(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("business_name"),
                        rs.getString("contact_name"),
                        rs.getString("phone"),
                        rs.getString("document_number"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("image_url"),
                        toLocalDateTime(rs.getTimestamp("starts_at")),
                        toLocalDateTime(rs.getTimestamp("ends_at")),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ))
                .single();
    }

    private String nextCode() {
        Long next = jdbcClient.sql("SELECT nextval('landing_promotion_request_code_seq')")
                .query(Long.class)
                .single();
        return "PR" + String.format("%06d", next);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo procesar los items de la promocion.", e);
        }
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String required(String value, String field) {
        String clean = clean(value);
        if (clean == null) throw new IllegalArgumentException("El campo " + field + " es obligatorio.");
        return clean;
    }

    private static String value(String value, String fallback) {
        String clean = clean(value);
        return clean == null ? fallback : clean.toUpperCase(Locale.ROOT);
    }

    private static Timestamp ts(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
