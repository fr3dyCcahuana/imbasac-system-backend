package com.paulfernandosr.possystembackend.mycustomers.application;

import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.mycustomers.infrastructure.adapter.input.dto.MyCustomersDtos.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterConversationService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.ConversationSummaryResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.StartConversationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyCustomersService {
    private static final Set<String> COMMERCIAL_STATUSES = Set.of(
            "SIN_CONTACTAR",
            "CONTACTADO",
            "SIN_RESPUESTA",
            "INTERESADO",
            "SEGUIMIENTO",
            "CLIENTE_FRECUENTE"
    );
    private static final Set<String> FOLLOWUP_TYPES = Set.of("LLAMAR", "WHATSAPP", "ENVIAR_CATALOGO", "ENVIAR_OFERTA", "OTRO");

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final WhatsAppCenterConversationService whatsappCenter;

    public PageResponse<MyCustomerRowResponse> list(
            int page,
            int size,
            String search,
            String status,
            String district,
            String tag,
            String lastContact,
            String followup,
            Long responsibleUserId,
            Authentication authentication
    ) {
        CurrentUser currentUser = currentUser(authentication);
        int cleanPage = Math.max(page, 0);
        int cleanSize = Math.min(Math.max(size, 1), 100);

        QueryParts query = buildPortfolioQuery(
                currentUser,
                search,
                status,
                district,
                tag,
                lastContact,
                followup,
                responsibleUserId
        );
        query.params.addValue("limit", cleanSize);
        query.params.addValue("offset", cleanPage * cleanSize);

        List<MyCustomerRowResponse> rows = jdbc.query("""
                SELECT *
                """ + query.fromSql + """
                ORDER BY COALESCE(last_contact_at, last_purchase_at, next_followup_at, assigned_at) DESC NULLS LAST,
                         legal_name ASC
                LIMIT :limit OFFSET :offset
                """, query.params, this::mapCustomerRow);

        long total = jdbc.queryForObject("SELECT count(*) " + query.fromSql, query.params, Long.class);
        return page(rows, cleanPage, cleanSize, total);
    }

    public MyCustomersSummaryResponse summary(
            String search,
            String status,
            String district,
            String tag,
            String lastContact,
            String followup,
            Long responsibleUserId,
            Authentication authentication
    ) {
        CurrentUser currentUser = currentUser(authentication);
        QueryParts query = buildPortfolioQuery(currentUser, search, status, district, tag, lastContact, followup, responsibleUserId);
        SummaryCounts counts = jdbc.queryForObject("""
                SELECT
                    count(*) AS assigned_customers,
                    count(*) FILTER (WHERE phone IS NOT NULL AND btrim(phone) <> '') AS with_contact,
                    count(*) FILTER (WHERE last_contact_at IS NULL OR last_contact_at < CURRENT_TIMESTAMP - interval '30 days') AS without_recent_contact,
                    count(*) FILTER (WHERE next_followup_at IS NOT NULL AND next_followup_status = 'PENDIENTE') AS pending_followups
                """ + query.fromSql, query.params, (rs, rowNum) -> new SummaryCounts(
                rs.getLong("assigned_customers"),
                rs.getLong("with_contact"),
                rs.getLong("without_recent_contact"),
                rs.getLong("pending_followups")
        ));

        long total = counts == null ? 0 : counts.assignedCustomers();
        long withContact = counts == null ? 0 : counts.withContact();
        double percent = total == 0 ? 0 : Math.round((withContact * 10000.0 / total)) / 100.0;
        return MyCustomersSummaryResponse.builder()
                .assignedCustomers(total)
                .withContact(withContact)
                .withContactPercent(percent)
                .withoutRecentContact(counts == null ? 0 : counts.withoutRecentContact())
                .pendingFollowups(counts == null ? 0 : counts.pendingFollowups())
                .canViewAllPortfolios(currentUser.canViewAllPortfolios())
                .currentUserId(currentUser.id())
                .build();
    }

    public MyCustomerDetailResponse detail(Long customerId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        MyCustomerRowResponse customer = findCustomerRow(customerId, currentUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado en cartera"));
        return MyCustomerDetailResponse.builder()
                .customer(customer)
                .recentActivity(activity(customerId, 0, 8, authentication).getPayload())
                .followups(followups(customerId, authentication))
                .materials(materials())
                .build();
    }

    public PageResponse<ActivityResponse> activity(Long customerId, int page, int size, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        int cleanPage = Math.max(page, 0);
        int cleanSize = Math.min(Math.max(size, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("limit", cleanSize)
                .addValue("offset", cleanPage * cleanSize);

        String from = """
                FROM (
                    SELECT a.id, a.customer_id, a.user_id,
                           trim(concat(coalesce(u.first_name,''), ' ', coalesce(u.last_name,''))) AS user_name,
                           a.type, a.title, a.detail, a.related_type, a.related_id, a.created_at
                    FROM customer_commercial_activity a
                    INNER JOIN users u ON u.id = a.user_id
                    WHERE a.customer_id = :customerId
                    UNION ALL
                    SELECT NULL::bigint AS id, p.customer_id, p.created_by AS user_id,
                           trim(concat(coalesce(u.first_name,''), ' ', coalesce(u.last_name,''))) AS user_name,
                           'PROFORMA_CREATED' AS type,
                           'Proforma registrada' AS title,
                           concat(coalesce(p.series,''), '-', p.number, ' · S/ ', p.total) AS detail,
                           'PROFORMA' AS related_type,
                           p.id AS related_id,
                           p.created_at
                    FROM proforma p
                    LEFT JOIN users u ON u.id = p.created_by
                    WHERE p.customer_id = :customerId
                    UNION ALL
                    SELECT NULL::bigint AS id, s.customer_id, s.created_by AS user_id,
                           trim(concat(coalesce(u.first_name,''), ' ', coalesce(u.last_name,''))) AS user_name,
                           'SALE_CREATED' AS type,
                           'Venta registrada' AS title,
                           concat('S/ ', s.total, ' · ', s.status) AS detail,
                           'SALE' AS related_type,
                           s.id AS related_id,
                           s.created_at
                    FROM sale s
                    LEFT JOIN users u ON u.id = s.created_by
                    WHERE s.customer_id = :customerId
                    UNION ALL
                    SELECT NULL::bigint AS id, cs.customer_id, cs.created_by AS user_id,
                           trim(concat(coalesce(u.first_name,''), ' ', coalesce(u.last_name,''))) AS user_name,
                           'COUNTER_SALE_CREATED' AS type,
                           'Venta por ventanilla registrada' AS title,
                           concat('S/ ', cs.total, ' · ', cs.status) AS detail,
                           'COUNTER_SALE' AS related_type,
                           cs.id AS related_id,
                           cs.created_at
                    FROM counter_sale cs
                    LEFT JOIN users u ON u.id = cs.created_by
                    WHERE cs.customer_id = :customerId
                ) x
                """;

        List<ActivityResponse> rows = jdbc.query("""
                SELECT *
                """ + from + """
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapActivity);
        long total = jdbc.queryForObject("SELECT count(*) " + from, params, Long.class);
        return page(rows, cleanPage, cleanSize, total);
    }

    public List<WhatsAppMessageResponse> whatsapp(Long customerId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        if (!whatsappHistoryRelationsAvailable()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("customerId", customerId);
        return jdbc.query("""
                WITH selected_customer AS (
                    SELECT regexp_replace(coalesce(phone,''), '\\D', '', 'g') AS phone_digits
                    FROM customers
                    WHERE id = :customerId
                ),
                selected_conversation AS (
                    SELECT conversation_id
                    FROM whatsapp.conversation_current_v w
                    CROSS JOIN selected_customer c
                    WHERE c.phone_digits <> ''
                      AND regexp_replace(coalesce(w.phone_number,''), '\\D', '', 'g') = c.phone_digits
                    ORDER BY COALESCE(last_message_at, last_event_at, conversation_created_at) DESC
                    LIMIT 1
                )
                SELECT m.conversation_id, m.id AS message_id, m.direction, m.text_body, m.message_type,
                       s.current_status, m.created_at, m.wa_timestamp
                FROM whatsapp.messages m
                LEFT JOIN whatsapp.message_current_status_v s ON s.wa_message_id = m.wa_message_id
                WHERE m.conversation_id = (SELECT conversation_id FROM selected_conversation)
                ORDER BY COALESCE(m.wa_timestamp, m.created_at) DESC, m.id DESC
                LIMIT 50
                """, params, this::mapWhatsAppMessage);
    }

    public List<OfferHistoryResponse> offers(Long customerId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        return jdbc.query("""
                SELECT a.id AS activity_id, a.type, a.title, a.detail, a.related_type, a.related_id,
                       a.created_at AS sent_at,
                       trim(concat(coalesce(u.first_name,''), ' ', coalesce(u.last_name,''))) AS sent_by_name
                FROM customer_commercial_activity a
                INNER JOIN users u ON u.id = a.user_id
                WHERE a.customer_id = :customerId
                  AND a.type IN ('CATALOG_SENT', 'OFFER_SENT')
                ORDER BY a.created_at DESC, a.id DESC
                """, new MapSqlParameterSource("customerId", customerId), (rs, rowNum) -> OfferHistoryResponse.builder()
                .activityId(rs.getLong("activity_id"))
                .type(rs.getString("type"))
                .title(rs.getString("title"))
                .detail(rs.getString("detail"))
                .relatedType(rs.getString("related_type"))
                .relatedId(getLong(rs, "related_id"))
                .sentAt(toLocalDateTime(rs.getTimestamp("sent_at")))
                .sentByName(nullIfBlank(rs.getString("sent_by_name")))
                .build());
    }

    @Transactional
    public FollowupResponse createFollowup(Long customerId, FollowupRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        if (request == null) {
            throw new InvalidCustomerException("Followup request is required");
        }
        LocalDateTime followupAt = parseDateTime(request.getFollowupAt(), "Fecha de seguimiento invalida");
        String type = normalizeType(request.getType(), FOLLOWUP_TYPES, "Tipo de seguimiento invalido");
        Long assignedUserId = activeResponsibleId(customerId);

        Long followupId = jdbc.queryForObject("""
                INSERT INTO customer_followups(
                    customer_id, assigned_user_id, followup_at, type, note, status, created_by, created_at
                ) VALUES (:customerId, :assignedUserId, :followupAt, :type, :note, 'PENDIENTE', :createdBy, CURRENT_TIMESTAMP)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("assignedUserId", assignedUserId)
                .addValue("followupAt", followupAt)
                .addValue("type", type)
                .addValue("note", nullIfBlank(request.getNote()))
                .addValue("createdBy", currentUser.id()), Long.class);

        recordActivity(customerId, currentUser.id(), "FOLLOWUP_CREATED", "Seguimiento programado", type + " · " + followupAt, "FOLLOWUP", followupId);
        upsertCommercialStatus(customerId, "SEGUIMIENTO", currentUser.id());
        return followups(customerId, authentication).stream()
                .filter(item -> Objects.equals(item.getId(), followupId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public FollowupResponse completeFollowup(Long customerId, Long followupId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        int updated = jdbc.update("""
                UPDATE customer_followups
                SET status = 'COMPLETADO',
                    completed_at = CURRENT_TIMESTAMP,
                    completed_by = :completedBy
                WHERE id = :followupId
                  AND customer_id = :customerId
                  AND status = 'PENDIENTE'
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("followupId", followupId)
                .addValue("completedBy", currentUser.id()));
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seguimiento no encontrado o ya cerrado");
        }
        recordActivity(customerId, currentUser.id(), "FOLLOWUP_COMPLETED", "Seguimiento completado", null, "FOLLOWUP", followupId);
        return followups(customerId, authentication).stream()
                .filter(item -> Objects.equals(item.getId(), followupId))
                .findFirst()
                .orElseThrow();
    }

    public List<FollowupResponse> followups(Long customerId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        return jdbc.query("""
                SELECT f.id, f.customer_id, f.assigned_user_id,
                       trim(concat(coalesce(au.first_name,''), ' ', coalesce(au.last_name,''))) AS assigned_user_name,
                       f.followup_at, f.type, f.note, f.status, f.created_by,
                       trim(concat(coalesce(cu.first_name,''), ' ', coalesce(cu.last_name,''))) AS created_by_name,
                       f.created_at, f.completed_at,
                       trim(concat(coalesce(done.first_name,''), ' ', coalesce(done.last_name,''))) AS completed_by_name
                FROM customer_followups f
                INNER JOIN users au ON au.id = f.assigned_user_id
                INNER JOIN users cu ON cu.id = f.created_by
                LEFT JOIN users done ON done.id = f.completed_by
                WHERE f.customer_id = :customerId
                ORDER BY CASE WHEN f.status = 'PENDIENTE' THEN 0 ELSE 1 END,
                         f.followup_at ASC,
                         f.id DESC
                LIMIT 50
                """, new MapSqlParameterSource("customerId", customerId), this::mapFollowup);
    }

    @Transactional
    public MyCustomerRowResponse updateCommercialStatus(Long customerId, CommercialStatusRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        if (request == null) {
            throw new InvalidCustomerException("Commercial status request is required");
        }
        String status = normalizeType(request.getStatus(), COMMERCIAL_STATUSES, "Estado comercial invalido");
        upsertCommercialStatus(customerId, status, currentUser.id());
        recordActivity(customerId, currentUser.id(), "STATUS_CHANGED", "Estado comercial actualizado", status, null, null);
        return findCustomerRow(customerId, currentUser).orElseThrow();
    }

    public List<TagResponse> tags() {
        return jdbc.query("""
                SELECT id, name
                FROM customer_tags
                WHERE active = TRUE
                ORDER BY name ASC
                """, new MapSqlParameterSource(), this::mapTag);
    }

    @Transactional
    public TagResponse addTag(Long customerId, TagRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        if (request == null || (request.getTagId() == null && nullIfBlank(request.getName()) == null)) {
            throw new InvalidCustomerException("Debe indicar una etiqueta");
        }

        Long tagId = request.getTagId();
        if (tagId == null) {
            tagId = jdbc.queryForObject("""
                    INSERT INTO customer_tags(name, active, created_by, created_at)
                    VALUES (:name, TRUE, :createdBy, CURRENT_TIMESTAMP)
                    ON CONFLICT (name) DO UPDATE SET active = TRUE
                    RETURNING id
                    """, new MapSqlParameterSource()
                    .addValue("name", nullIfBlank(request.getName()))
                    .addValue("createdBy", currentUser.id()), Long.class);
        }

        jdbc.update("""
                INSERT INTO customer_tag_assignments(customer_id, tag_id, created_by, created_at)
                VALUES (:customerId, :tagId, :createdBy, CURRENT_TIMESTAMP)
                ON CONFLICT (customer_id, tag_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("tagId", tagId)
                .addValue("createdBy", currentUser.id()));
        TagResponse tag = findTag(tagId).orElseThrow();
        recordActivity(customerId, currentUser.id(), "TAG_ADDED", "Etiqueta agregada", tag.getName(), "TAG", tagId);
        return tag;
    }

    @Transactional
    public void removeTag(Long customerId, Long tagId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        Optional<TagResponse> tag = findTag(tagId);
        int deleted = jdbc.update("""
                DELETE FROM customer_tag_assignments
                WHERE customer_id = :customerId
                  AND tag_id = :tagId
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("tagId", tagId));
        if (deleted > 0) {
            recordActivity(customerId, currentUser.id(), "TAG_REMOVED", "Etiqueta retirada", tag.map(TagResponse::getName).orElse(null), "TAG", tagId);
        }
    }

    public List<MaterialResponse> materials() {
        return jdbc.query("""
                SELECT 'PROMOTION' AS type, id, title AS name, subtitle AS description, status,
                       starts_at, ends_at
                FROM promotion_campaign
                WHERE status = 'ACTIVE'
                  AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
                  AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)
                  AND channel IN ('WHATSAPP', 'BOTH')
                UNION ALL
                SELECT 'PRODUCT_OFFER' AS type, id, name, code AS description, status,
                       starts_at::timestamp AS starts_at, ends_at::timestamp AS ends_at
                FROM product_basic_offer
                WHERE status = 'ACTIVE'
                  AND starts_at <= CURRENT_DATE
                  AND ends_at >= CURRENT_DATE
                ORDER BY type, name
                LIMIT 50
                """, new MapSqlParameterSource(), this::mapMaterial);
    }

    @Transactional
    public SendCommercialResponse sendWhatsApp(Long customerId, WhatsAppTextRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        CustomerContact contact = customerContact(customerId);
        if (nullIfBlank(contact.phone()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no tiene telefono registrado");
        }
        String message = nullIfBlank(request == null ? null : request.getMessage());
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje no puede estar vacio");
        }

        ConversationSummaryResponse conversation = whatsappCenter.startConversation(StartConversationRequest.builder()
                .phoneNumber(contact.phone())
                .profileName(contact.name())
                .body(message)
                .text(message)
                .message(message)
                .advisorId(currentUser.id())
                .advisorName(currentUser.displayName())
                .build());
        Long activityId = recordActivity(customerId, currentUser.id(), "WHATSAPP_SENT", "WhatsApp enviado", message, "WHATSAPP_CONVERSATION", conversation.getConversationId());
        upsertCommercialStatus(customerId, "CONTACTADO", currentUser.id());
        return SendCommercialResponse.builder()
                .customerId(customerId)
                .conversationId(conversation.getConversationId())
                .activityId(activityId)
                .status("SENT")
                .build();
    }

    @Transactional
    public SendCommercialResponse sendMaterial(Long customerId, SendMaterialRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        assertCanAccessCustomer(customerId, currentUser);
        if (request == null) {
            throw new InvalidCustomerException("Material request is required");
        }
        String materialType = normalizeMaterialType(request.getMaterialType());
        Long materialId = request.getMaterialId();
        MaterialResponse material = findActiveMaterial(materialType, materialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Material comercial no disponible"));
        String message = nullIfBlank(request.getMessage());
        if (message == null) {
            message = defaultMaterialMessage(material);
        }

        SendCommercialResponse sent = sendWhatsApp(customerId, WhatsAppTextRequest.builder().message(message).build(), authentication);
        String activityType = "PROMOTION".equals(materialType) || "PRODUCT_OFFER".equals(materialType) ? "OFFER_SENT" : "CATALOG_SENT";
        Long activityId = recordActivity(customerId, currentUser.id(), activityType, material.getName(), message, materialType, materialId);
        return SendCommercialResponse.builder()
                .customerId(customerId)
                .conversationId(sent.getConversationId())
                .activityId(activityId)
                .status("SENT")
                .build();
    }

    private QueryParts buildPortfolioQuery(
            CurrentUser currentUser,
            String search,
            String status,
            String district,
            String tag,
            String lastContact,
            String followup,
            Long responsibleUserId
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("""
                WHERE ca.active = TRUE
                """);

        if (currentUser.canViewAllPortfolios() && responsibleUserId != null) {
            where.append(" AND ca.user_id = :responsibleUserId ");
            params.addValue("responsibleUserId", responsibleUserId);
        } else {
            where.append(" AND ca.user_id = :currentUserId ");
            params.addValue("currentUserId", currentUser.id());
        }

        String cleanSearch = nullIfBlank(search);
        if (cleanSearch != null) {
            where.append("""
                    AND (
                        c.legal_name ILIKE :search
                        OR c.document_number ILIKE :search
                        OR c.phone ILIKE :search
                    )
                    """);
            params.addValue("search", "%" + cleanSearch + "%");
        }
        String cleanStatus = nullIfBlank(status);
        if (cleanStatus != null) {
            where.append(" AND COALESCE(cp.status, 'SIN_CONTACTAR') = :status ");
            params.addValue("status", cleanStatus.toUpperCase(Locale.ROOT));
        }
        String cleanDistrict = nullIfBlank(district);
        if (cleanDistrict != null) {
            where.append(" AND COALESCE(address_main.district, c.district, '') ILIKE :district ");
            params.addValue("district", "%" + cleanDistrict + "%");
        }
        String cleanTag = nullIfBlank(tag);
        if (cleanTag != null) {
            where.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM customer_tag_assignments cta
                        INNER JOIN customer_tags ct ON ct.id = cta.tag_id
                        WHERE cta.customer_id = c.id
                          AND ct.active = TRUE
                          AND (ct.name ILIKE :tag OR ct.id::text = :tagExact)
                    )
                    """);
            params.addValue("tag", "%" + cleanTag + "%");
            params.addValue("tagExact", cleanTag);
        }
        appendLastContactFilter(where, params, lastContact);
        appendFollowupFilter(where, params, followup);

        String whatsappContactJoin = whatsappConversationViewAvailable()
                ? """
                LEFT JOIN LATERAL (
                    SELECT max(COALESCE(w.last_message_at, w.last_event_at, w.conversation_created_at))::timestamp AS last_message_at
                    FROM whatsapp.conversation_current_v w
                    WHERE regexp_replace(coalesce(w.phone_number,''), '\\D', '', 'g') = regexp_replace(coalesce(c.phone,''), '\\D', '', 'g')
                      AND coalesce(c.phone, '') <> ''
                ) wa_contact ON TRUE
                """
                : """
                LEFT JOIN LATERAL (
                    SELECT NULL::timestamp AS last_message_at
                ) wa_contact ON TRUE
                """;

        String fromSql = """
                FROM customers c
                INNER JOIN customer_assignments ca ON ca.customer_id = c.id
                INNER JOIN users responsible ON responsible.id = ca.user_id
                LEFT JOIN customer_commercial_profile cp ON cp.customer_id = c.id
                LEFT JOIN LATERAL (
                    SELECT address, district
                    FROM customer_address
                    WHERE customer_id = c.id
                      AND enabled = TRUE
                    ORDER BY fiscal DESC, position ASC, id ASC
                    LIMIT 1
                ) address_main ON TRUE
                LEFT JOIN LATERAL (
                    SELECT event_at AS last_purchase_at, total AS last_purchase_total, source AS last_purchase_source
                    FROM (
                        SELECT s.created_at AS event_at, s.total, 'VENTA' AS source
                        FROM sale s
                        WHERE s.customer_id = c.id
                          AND COALESCE(s.status, '') <> 'ANULADA'
                        UNION ALL
                        SELECT cs.created_at AS event_at, cs.total, 'VENTANILLA' AS source
                        FROM counter_sale cs
                        WHERE cs.customer_id = c.id
                          AND COALESCE(cs.status, '') <> 'ANULADA'
                    ) purchases
                    ORDER BY event_at DESC
                    LIMIT 1
                ) last_purchase ON TRUE
                LEFT JOIN LATERAL (
                    SELECT p.created_at AS last_proforma_at, p.total AS last_proforma_total, p.status AS last_proforma_status
                    FROM proforma p
                    WHERE p.customer_id = c.id
                    ORDER BY p.created_at DESC
                    LIMIT 1
                ) last_proforma ON TRUE
                LEFT JOIN LATERAL (
                    SELECT f.followup_at AS next_followup_at, f.type AS next_followup_type, f.status AS next_followup_status
                    FROM customer_followups f
                    WHERE f.customer_id = c.id
                      AND f.status = 'PENDIENTE'
                    ORDER BY f.followup_at ASC, f.id ASC
                    LIMIT 1
                ) next_followup ON TRUE
                LEFT JOIN LATERAL (
                    SELECT max(a.created_at) AS last_activity_at
                    FROM customer_commercial_activity a
                    WHERE a.customer_id = c.id
                      AND a.type IN ('WHATSAPP_SENT','CATALOG_SENT','OFFER_SENT','FOLLOWUP_COMPLETED')
                ) crm_contact ON TRUE
                """ + whatsappContactJoin + """
                LEFT JOIN LATERAL (
                    SELECT max(f.completed_at) AS completed_at
                    FROM customer_followups f
                    WHERE f.customer_id = c.id
                      AND f.status = 'COMPLETADO'
                ) completed_followup ON TRUE
                LEFT JOIN LATERAL (
                    SELECT max(v.at) AS last_contact_at
                    FROM (VALUES (crm_contact.last_activity_at), (wa_contact.last_message_at), (completed_followup.completed_at)) AS v(at)
                ) last_contact ON TRUE
                LEFT JOIN LATERAL (
                    SELECT string_agg(ct.id::text || ':' || ct.name, '|' ORDER BY ct.name) AS tags
                    FROM customer_tag_assignments cta
                    INNER JOIN customer_tags ct ON ct.id = cta.tag_id
                    WHERE cta.customer_id = c.id
                      AND ct.active = TRUE
                ) tags ON TRUE
                """ + where + """
                """;

        String selectFrom = """
                FROM (
                    SELECT
                        c.id AS customer_id,
                        c.legal_name,
                        c.document_type,
                        c.document_number,
                        c.phone,
                        c.email,
                        COALESCE(address_main.address, c.address) AS address,
                        COALESCE(address_main.district, c.district) AS district,
                        ca.user_id AS responsible_user_id,
                        trim(concat(coalesce(responsible.first_name,''), ' ', coalesce(responsible.last_name,''))) AS responsible_name,
                        ca.assigned_at,
                        COALESCE(cp.status, 'SIN_CONTACTAR') AS commercial_status,
                        last_purchase.last_purchase_at,
                        last_purchase.last_purchase_total,
                        last_purchase.last_purchase_source,
                        last_proforma.last_proforma_at,
                        last_proforma.last_proforma_total,
                        last_proforma.last_proforma_status,
                        last_contact.last_contact_at,
                        CASE
                            WHEN wa_contact.last_message_at IS NOT NULL AND wa_contact.last_message_at = last_contact.last_contact_at THEN 'WHATSAPP'
                            WHEN completed_followup.completed_at IS NOT NULL AND completed_followup.completed_at = last_contact.last_contact_at THEN 'FOLLOWUP'
                            WHEN crm_contact.last_activity_at IS NOT NULL AND crm_contact.last_activity_at = last_contact.last_contact_at THEN 'CRM'
                            ELSE NULL
                        END AS last_contact_source,
                        next_followup.next_followup_at,
                        next_followup.next_followup_type,
                        next_followup.next_followup_status,
                        CASE
                            WHEN c.phone IS NULL OR btrim(c.phone) = '' THEN 'SIN_TELEFONO'
                            WHEN wa_contact.last_message_at IS NULL THEN 'LISTO_PARA_CONTACTAR'
                            WHEN wa_contact.last_message_at >= CURRENT_TIMESTAMP - interval '7 days' THEN 'CONVERSACION_ACTIVA'
                            ELSE 'SIN_CONTACTO_RECIENTE'
                        END AS whatsapp_status,
                        tags.tags
                """ + fromSql + """
                ) portfolio
                """;
        return new QueryParts(selectFrom, params);
    }

    private boolean whatsappConversationViewAvailable() {
        return relationExists("whatsapp.conversation_current_v");
    }

    private boolean whatsappHistoryRelationsAvailable() {
        return relationExists("whatsapp.conversation_current_v")
                && relationExists("whatsapp.messages")
                && relationExists("whatsapp.message_current_status_v");
    }

    private boolean relationExists(String relationName) {
        Boolean exists = jdbc.queryForObject(
                "SELECT to_regclass(:relationName) IS NOT NULL",
                new MapSqlParameterSource("relationName", relationName),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private void appendLastContactFilter(StringBuilder where, MapSqlParameterSource params, String lastContact) {
        String filter = nullIfBlank(lastContact);
        if (filter == null || "TODOS".equalsIgnoreCase(filter)) return;
        switch (filter.toUpperCase(Locale.ROOT)) {
            case "SIN_CONTACTO" -> where.append(" AND last_contact.last_contact_at IS NULL ");
            case "RECIENTE_7" -> where.append(" AND last_contact.last_contact_at >= CURRENT_TIMESTAMP - interval '7 days' ");
            case "RECIENTE_30" -> where.append(" AND last_contact.last_contact_at >= CURRENT_TIMESTAMP - interval '30 days' ");
            case "ANTIGUO_30" -> where.append(" AND (last_contact.last_contact_at IS NULL OR last_contact.last_contact_at < CURRENT_TIMESTAMP - interval '30 days') ");
            default -> {
            }
        }
    }

    private void appendFollowupFilter(StringBuilder where, MapSqlParameterSource params, String followup) {
        String filter = nullIfBlank(followup);
        if (filter == null || "TODOS".equalsIgnoreCase(filter)) return;
        switch (filter.toUpperCase(Locale.ROOT)) {
            case "PENDIENTES" -> where.append(" AND next_followup.next_followup_at IS NOT NULL ");
            case "HOY" -> where.append(" AND next_followup.next_followup_at::date = CURRENT_DATE ");
            case "VENCIDOS" -> where.append(" AND next_followup.next_followup_at < CURRENT_TIMESTAMP ");
            case "SIN_SEGUIMIENTO" -> where.append(" AND next_followup.next_followup_at IS NULL ");
            default -> {
            }
        }
    }

    private Optional<MyCustomerRowResponse> findCustomerRow(Long customerId, CurrentUser currentUser) {
        QueryParts query = buildPortfolioQuery(currentUser, null, null, null, null, null, null, null);
        query.params.addValue("customerId", customerId);
        List<MyCustomerRowResponse> rows = jdbc.query("""
                SELECT *
                """ + query.fromSql + """
                WHERE customer_id = :customerId
                """, query.params, this::mapCustomerRow);
        return rows.stream().findFirst();
    }

    private void assertCanAccessCustomer(Long customerId, CurrentUser currentUser) {
        if (customerId == null) {
            throw new InvalidCustomerException("Customer id is required");
        }
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM customer_assignments ca
                    WHERE ca.customer_id = :customerId
                      AND ca.active = TRUE
                      AND (:canViewAll = TRUE OR ca.user_id = :currentUserId)
                )
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("currentUserId", currentUser.id())
                .addValue("canViewAll", currentUser.canViewAllPortfolios()), Boolean.class);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este cliente");
        }
    }

    private Long activeResponsibleId(Long customerId) {
        return jdbc.queryForObject("""
                SELECT user_id
                FROM customer_assignments
                WHERE customer_id = :customerId
                  AND active = TRUE
                LIMIT 1
                """, new MapSqlParameterSource("customerId", customerId), Long.class);
    }

    private void upsertCommercialStatus(Long customerId, String status, Long userId) {
        jdbc.update("""
                INSERT INTO customer_commercial_profile(customer_id, status, updated_by, updated_at)
                VALUES (:customerId, :status, :updatedBy, CURRENT_TIMESTAMP)
                ON CONFLICT (customer_id)
                DO UPDATE SET status = EXCLUDED.status,
                              updated_by = EXCLUDED.updated_by,
                              updated_at = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("status", status)
                .addValue("updatedBy", userId));
    }

    private Long recordActivity(Long customerId, Long userId, String type, String title, String detail, String relatedType, Long relatedId) {
        return jdbc.queryForObject("""
                INSERT INTO customer_commercial_activity(
                    customer_id, user_id, type, title, detail, related_type, related_id, created_at
                ) VALUES (:customerId, :userId, :type, :title, :detail, :relatedType, :relatedId, CURRENT_TIMESTAMP)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("userId", userId)
                .addValue("type", type)
                .addValue("title", title)
                .addValue("detail", detail)
                .addValue("relatedType", relatedType)
                .addValue("relatedId", relatedId), Long.class);
    }

    private CustomerContact customerContact(Long customerId) {
        return jdbc.queryForObject("""
                SELECT legal_name, phone
                FROM customers
                WHERE id = :customerId
                """, new MapSqlParameterSource("customerId", customerId),
                (rs, rowNum) -> new CustomerContact(rs.getString("legal_name"), rs.getString("phone")));
    }

    private Optional<TagResponse> findTag(Long tagId) {
        if (tagId == null) return Optional.empty();
        return jdbc.query("""
                SELECT id, name
                FROM customer_tags
                WHERE id = :tagId
                  AND active = TRUE
                """, new MapSqlParameterSource("tagId", tagId), this::mapTag).stream().findFirst();
    }

    private Optional<MaterialResponse> findActiveMaterial(String type, Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return materials().stream()
                .filter(item -> Objects.equals(item.getType(), type) && Objects.equals(item.getId(), id))
                .findFirst();
    }

    private MyCustomerRowResponse mapCustomerRow(ResultSet rs, int rowNum) throws SQLException {
        LocalDateTime lastContactAt = toLocalDateTime(rs.getTimestamp("last_contact_at"));
        LocalDateTime nextFollowupAt = toLocalDateTime(rs.getTimestamp("next_followup_at"));
        return MyCustomerRowResponse.builder()
                .customerId(rs.getLong("customer_id"))
                .legalName(rs.getString("legal_name"))
                .documentType(rs.getString("document_type"))
                .documentNumber(rs.getString("document_number"))
                .phone(rs.getString("phone"))
                .email(rs.getString("email"))
                .address(rs.getString("address"))
                .district(rs.getString("district"))
                .responsibleUserId(rs.getLong("responsible_user_id"))
                .responsibleName(nullIfBlank(rs.getString("responsible_name")))
                .commercialStatus(rs.getString("commercial_status"))
                .lastPurchaseAt(toLocalDateTime(rs.getTimestamp("last_purchase_at")))
                .lastPurchaseTotal(rs.getBigDecimal("last_purchase_total"))
                .lastPurchaseSource(rs.getString("last_purchase_source"))
                .lastProformaAt(toLocalDateTime(rs.getTimestamp("last_proforma_at")))
                .lastProformaTotal(rs.getBigDecimal("last_proforma_total"))
                .lastProformaStatus(rs.getString("last_proforma_status"))
                .lastContactAt(lastContactAt)
                .lastContactSource(rs.getString("last_contact_source"))
                .nextFollowupAt(nextFollowupAt)
                .nextFollowupType(rs.getString("next_followup_type"))
                .nextFollowupStatus(rs.getString("next_followup_status"))
                .whatsappStatus(rs.getString("whatsapp_status"))
                .tags(parseTags(rs.getString("tags")))
                .build();
    }

    private ActivityResponse mapActivity(ResultSet rs, int rowNum) throws SQLException {
        return ActivityResponse.builder()
                .id(getLong(rs, "id"))
                .customerId(rs.getLong("customer_id"))
                .userId(getLong(rs, "user_id"))
                .userName(nullIfBlank(rs.getString("user_name")))
                .type(rs.getString("type"))
                .title(rs.getString("title"))
                .detail(rs.getString("detail"))
                .relatedType(rs.getString("related_type"))
                .relatedId(getLong(rs, "related_id"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private FollowupResponse mapFollowup(ResultSet rs, int rowNum) throws SQLException {
        return FollowupResponse.builder()
                .id(rs.getLong("id"))
                .customerId(rs.getLong("customer_id"))
                .assignedUserId(rs.getLong("assigned_user_id"))
                .assignedUserName(nullIfBlank(rs.getString("assigned_user_name")))
                .followupAt(toLocalDateTime(rs.getTimestamp("followup_at")))
                .type(rs.getString("type"))
                .note(rs.getString("note"))
                .status(rs.getString("status"))
                .createdBy(rs.getLong("created_by"))
                .createdByName(nullIfBlank(rs.getString("created_by_name")))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .completedAt(toLocalDateTime(rs.getTimestamp("completed_at")))
                .completedByName(nullIfBlank(rs.getString("completed_by_name")))
                .build();
    }

    private WhatsAppMessageResponse mapWhatsAppMessage(ResultSet rs, int rowNum) throws SQLException {
        return WhatsAppMessageResponse.builder()
                .conversationId(rs.getLong("conversation_id"))
                .messageId(rs.getLong("message_id"))
                .direction(rs.getString("direction"))
                .textBody(rs.getString("text_body"))
                .messageType(rs.getString("message_type"))
                .currentStatus(rs.getString("current_status"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .waTimestamp(toLocalDateTime(rs.getTimestamp("wa_timestamp")))
                .build();
    }

    private TagResponse mapTag(ResultSet rs, int rowNum) throws SQLException {
        return TagResponse.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .build();
    }

    private MaterialResponse mapMaterial(ResultSet rs, int rowNum) throws SQLException {
        return MaterialResponse.builder()
                .type(rs.getString("type"))
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .status(rs.getString("status"))
                .startsAt(toLocalDateTime(rs.getTimestamp("starts_at")))
                .endsAt(toLocalDateTime(rs.getTimestamp("ends_at")))
                .build();
    }

    private List<TagResponse> parseTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(item -> item.split(":", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> TagResponse.builder().id(Long.valueOf(parts[0])).name(parts[1]).build())
                .toList();
    }

    private <T> PageResponse<T> page(List<T> rows, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PageResponse.<T>builder()
                .payload(rows)
                .metadata(PageMetadata.builder()
                        .page(page)
                        .size(size)
                        .numberOfElements(rows.size())
                        .totalElements(total)
                        .totalPages(totalPages)
                        .build())
                .build();
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
        Set<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                permissions.contains("MANAGE_MY_CUSTOMERS_ALL") || permissions.contains("MANAGE_CUSTOMERS")
        );
    }

    private LocalDateTime parseDateTime(String value, String errorMessage) {
        String clean = nullIfBlank(value);
        if (clean == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        try {
            return LocalDateTime.parse(clean);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(clean).toLocalDateTime();
            } catch (DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
    }

    private String normalizeType(String value, Set<String> allowed, String errorMessage) {
        String clean = nullIfBlank(value);
        if (clean == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        String normalized = clean.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private String normalizeMaterialType(String value) {
        String type = nullIfBlank(value);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de material requerido");
        }
        String normalized = type.toUpperCase(Locale.ROOT);
        if (!Set.of("PROMOTION", "PRODUCT_OFFER", "CATALOG").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de material no soportado");
        }
        return normalized;
    }

    private String defaultMaterialMessage(MaterialResponse material) {
        return "Hola, te compartimos informacion comercial de IMBASAC: " + material.getName() + ". Cualquier consulta estamos para ayudarte.";
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String nullIfBlank(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record QueryParts(String fromSql, MapSqlParameterSource params) {
    }

    private record SummaryCounts(long assignedCustomers, long withContact, long withoutRecentContact, long pendingFollowups) {
    }

    private record CustomerContact(String name, String phone) {
    }

    private record CurrentUser(Long id, String username, String firstName, String lastName, boolean canViewAllPortfolios) {
        private String displayName() {
            String fullName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            return fullName.isBlank() ? username : fullName;
        }
    }
}
