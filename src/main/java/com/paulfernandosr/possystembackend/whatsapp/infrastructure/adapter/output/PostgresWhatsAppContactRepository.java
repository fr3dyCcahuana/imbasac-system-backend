package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppContact;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppContactRepository implements WhatsAppContactRepository {
    private final JdbcClient jdbcClient;

    private final RowMapper<WhatsAppContact> mapper = (rs, rowNum) -> WhatsAppContact.builder()
            .id(rs.getLong("id"))
            .waId(rs.getString("wa_id"))
            .phoneNumber(rs.getString("phone_number"))
            .profileName(rs.getString("profile_name"))
            .customerId(rs.getObject("customer_id", Long.class))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class))
            .build();

    @Override
    public WhatsAppContact upsertByWaId(String waId, String phoneNumber, String profileName) {
        String sql = """
                INSERT INTO whatsapp_contacts (wa_id, phone_number, profile_name)
                VALUES (:waId, :phoneNumber, :profileName)
                ON CONFLICT (wa_id) DO UPDATE SET
                    phone_number = COALESCE(EXCLUDED.phone_number, whatsapp_contacts.phone_number),
                    profile_name = COALESCE(EXCLUDED.profile_name, whatsapp_contacts.profile_name),
                    updated_at = now()
                RETURNING *
                """;
        return jdbcClient.sql(sql)
                .param("waId", waId)
                .param("phoneNumber", phoneNumber)
                .param("profileName", profileName)
                .query(mapper)
                .single();
    }

    @Override
    public Optional<WhatsAppContact> findByWaId(String waId) {
        return jdbcClient.sql("SELECT * FROM whatsapp_contacts WHERE wa_id = :waId")
                .param("waId", waId)
                .query(mapper)
                .optional();
    }
}
