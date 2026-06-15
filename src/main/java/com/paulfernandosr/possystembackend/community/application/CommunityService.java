package com.paulfernandosr.possystembackend.community.application;

import com.paulfernandosr.possystembackend.community.infrastructure.adapter.input.dto.CommunityDtos.*;
import com.paulfernandosr.possystembackend.community.infrastructure.adapter.output.CommunityFileStorageService;
import com.paulfernandosr.possystembackend.community.infrastructure.adapter.output.CommunityFileStorageService.StoredCommunityFile;
import com.paulfernandosr.possystembackend.community.infrastructure.adapter.output.CommunityNotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private static final Set<String> TYPES = Set.of(
            "INFORMATIVA",
            "IMPORTANTE",
            "URGENTE"
    );
    private static final Set<String> STATUSES = Set.of("REGISTRADO", "EN_REVISION", "RESUELTO");
    private static final Set<String> REACTIONS = Set.of(
            "ENTENDIDO",
            "EXCELENTE",
            "FELICITACIONES",
            "REVISAR"
    );

    private final JdbcClient jdbcClient;
    private final CommunityFileStorageService fileStorageService;
    private final CommunityNotificationSseService sseService;

    @Value("${app.files.community-public-path:/files/community}")
    private String publicPath;

    public List<CommunityGroupDto> findGroups() {
        return jdbcClient.sql("""
                SELECT id, name, description, color, icon, active, created_at, updated_at
                FROM community_group
                WHERE deleted_at IS NULL
                ORDER BY active DESC, name ASC
                """)
                .query((rs, rowNum) -> new CommunityGroupDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("color"),
                        rs.getString("icon"),
                        rs.getBoolean("active"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ))
                .list();
    }

    @Transactional
    public CommunityGroupDto createGroup(CreateGroupRequest request) {
        String name = required(request.name(), "nombre");
        Long id = jdbcClient.sql("""
                INSERT INTO community_group (name, description, color, icon, active)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """)
                .params(
                        name,
                        clean(request.description()),
                        clean(request.color()),
                        clean(request.icon()),
                        request.active() == null || request.active()
                )
                .query(Long.class)
                .single();
        return findGroupById(id);
    }

    @Transactional
    public CommunityGroupDto updateGroup(Long id, CreateGroupRequest request) {
        int updated = jdbcClient.sql("""
                UPDATE community_group
                   SET name = ?,
                       description = ?,
                       color = ?,
                       icon = ?,
                       active = ?,
                       updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .params(
                        required(request.name(), "nombre"),
                        clean(request.description()),
                        clean(request.color()),
                        clean(request.icon()),
                        request.active() == null || request.active(),
                        id
                )
                .update();
        if (updated == 0) throw new IllegalArgumentException("Grupo no encontrado.");
        return findGroupById(id);
    }

    @Transactional
    public void deleteGroup(Long id) {
        int updated = jdbcClient.sql("""
                UPDATE community_group
                   SET deleted_at = NOW(), active = FALSE, updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .update();
        if (updated == 0) throw new IllegalArgumentException("Grupo no encontrado.");
    }

    public List<CommunityPostSummaryDto> findPosts(CommunityPostFilters filters, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        int audience = audienceCount();
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT cp.id,
                       cp.group_id,
                       cg.name AS group_name,
                       cg.color AS group_color,
                       cg.icon AS group_icon,
                       cp.type,
                       cp.status,
                       cp.title,
                       cp.content,
                       cp.pinned,
                       cp.author_user_id,
                       u.username AS author_username,
                       u.first_name AS author_first_name,
                       u.last_name AS author_last_name,
                       cp.created_at,
                       cp.updated_at,
                       cp.resolved_at,
                       COALESCE(cc.comment_count, 0) AS comment_count
                FROM community_post cp
                JOIN community_group cg ON cg.id = cp.group_id
                JOIN users u ON u.id = cp.author_user_id
                LEFT JOIN (
                    SELECT post_id, COUNT(*)::int AS comment_count
                    FROM community_comment
                    WHERE deleted_at IS NULL
                    GROUP BY post_id
                ) cc ON cc.post_id = cp.id
                WHERE cp.deleted_at IS NULL
                  AND cg.deleted_at IS NULL
                """);

        if (clean(filters.query()) != null) {
            params.add("%" + clean(filters.query()).toUpperCase(Locale.ROOT) + "%");
            sql.append("""
                      AND (
                          UPPER(cp.title) LIKE ?
                          OR UPPER(cp.content) LIKE ?
                          OR UPPER(cg.name) LIKE ?
                          OR EXISTS (
                              SELECT 1
                              FROM community_post_product cpp
                              JOIN product p ON p.id = cpp.product_id
                              WHERE cpp.post_id = cp.id
                                AND (UPPER(p.sku) LIKE ? OR UPPER(p.name) LIKE ? OR UPPER(COALESCE(p.brand, '')) LIKE ?)
                          )
                      )
                    """);
            String q = String.valueOf(params.get(params.size() - 1));
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (filters.groupId() != null) {
            sql.append(" AND cp.group_id = ? ");
            params.add(filters.groupId());
        }
        if (clean(filters.status()) != null && !"ALL".equalsIgnoreCase(clean(filters.status()))) {
            sql.append(" AND cp.status = ? ");
            params.add(status(filters.status()));
        }
        if (clean(filters.type()) != null && !"ALL".equalsIgnoreCase(clean(filters.type()))) {
            sql.append(" AND cp.type = ? ");
            params.add(type(filters.type()));
        }
        if (filters.userId() != null) {
            sql.append(" AND cp.author_user_id = ? ");
            params.add(filters.userId());
        }
        if (filters.dateFrom() != null) {
            sql.append(" AND cp.created_at::date >= ? ");
            params.add(filters.dateFrom());
        }
        if (filters.dateTo() != null) {
            sql.append(" AND cp.created_at::date <= ? ");
            params.add(filters.dateTo());
        }
        if (filters.pinned() != null) {
            sql.append(" AND cp.pinned = ? ");
            params.add(filters.pinned());
        }
        if (filters.resolved() != null) {
            sql.append(Boolean.TRUE.equals(filters.resolved())
                    ? " AND cp.status = 'RESUELTO' "
                    : " AND cp.status <> 'RESUELTO' ");
        }

        int limit = filters.limit() == null ? 10 : Math.max(1, Math.min(filters.limit(), 50));
        long offset = filters.offset() == null ? 0L : Math.max(0L, filters.offset().longValue());
        sql.append(" ORDER BY cp.pinned DESC, cp.updated_at DESC, cp.created_at DESC, cp.id DESC LIMIT ? OFFSET ? ");
        params.add(limit);
        params.add(offset);

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query((rs, rowNum) -> {
                    Long postId = rs.getLong("id");
                    Long authorId = rs.getLong("author_user_id");
                    String status = rs.getString("status");
                    List<CommunityReactionSummaryDto> reactions = findReactionSummary(postId);
                    return new CommunityPostSummaryDto(
                            postId,
                            rs.getLong("group_id"),
                            rs.getString("group_name"),
                            rs.getString("group_color"),
                            rs.getString("group_icon"),
                            rs.getString("type"),
                            status,
                            rs.getString("title"),
                            preview(rs.getString("content")),
                            rs.getBoolean("pinned"),
                            userDto(
                                    authorId,
                                    rs.getString("author_username"),
                                    rs.getString("author_first_name"),
                                    rs.getString("author_last_name")
                            ),
                            findProducts(postId),
                            findAttachments(postId, null),
                            rs.getInt("comment_count"),
                            reactions,
                            myReaction(postId, currentUser.id()),
                            reactionTotal(reactions),
                            readStats(postId, audience, currentUser),
                            findPoll(postId, currentUser),
                            canEditPost(authorId, status, currentUser),
                            currentUser.has("MANAGE_FORUM_DELETE"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at")),
                            toLocalDateTime(rs.getTimestamp("resolved_at"))
                    );
                })
                .list();
    }

    public CommunityPostDto findPostById(Long id, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        PostBase base = findPostBase(id);
        return toPostDto(base, currentUser);
    }

    @Transactional
    public CommunityPostDto createPost(CreatePostRequest request, List<MultipartFile> files, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        validatePost(request.groupId(), request.type(), request.title(), request.content(), request.productIds());
        ensureGroupExists(request.groupId());

        Long id = jdbcClient.sql("""
                INSERT INTO community_post (group_id, type, status, title, content, pinned, author_user_id)
                VALUES (?, ?, 'REGISTRADO', ?, ?, ?, ?)
                RETURNING id
                """)
                .params(
                        request.groupId(),
                        type(request.type()),
                        required(request.title(), "titulo"),
                        required(request.content(), "contenido"),
                        Boolean.TRUE.equals(request.pinned()) && currentUser.has("MANAGE_FORUM_STATUS"),
                        currentUser.id()
                )
                .query(Long.class)
                .single();

        replaceProducts(id, request.productIds());
        createPoll(id, request.poll());
        storePostFiles(id, files);
        notifyUsers(
                findForumViewUserIdsExcept(currentUser.id()),
                "COMMUNITY_POST_CREATED",
                "Nueva publicacion en Comunidad interna",
                currentUser.displayName() + " creo una publicacion: " + required(request.title(), "titulo"),
                id,
                null
        );

        return findPostById(id, authentication);
    }

    @Transactional
    public CommunityPostDto updatePost(Long id, UpdatePostRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        PostBase current = findPostBase(id);
        if (!canEditPost(current.author().id(), current.status(), currentUser)) {
            throw new IllegalArgumentException("No tienes permiso para editar esta publicacion.");
        }
        validatePost(request.groupId(), request.type(), request.title(), request.content(), request.productIds());
        ensureGroupExists(request.groupId());

        jdbcClient.sql("""
                UPDATE community_post
                   SET group_id = ?,
                       type = ?,
                       title = ?,
                       content = ?,
                       pinned = CASE WHEN ? THEN ? ELSE pinned END,
                       updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .params(
                        request.groupId(),
                        type(request.type()),
                        required(request.title(), "titulo"),
                        required(request.content(), "contenido"),
                        currentUser.has("MANAGE_FORUM_STATUS"),
                        Boolean.TRUE.equals(request.pinned()),
                        id
                )
                .update();

        replaceProducts(id, request.productIds());
        return findPostById(id, authentication);
    }

    @Transactional
    public CommunityPostDto updateStatus(Long id, UpdateStatusRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_FORUM_STATUS")) {
            throw new IllegalArgumentException("No tienes permiso para cambiar el estado.");
        }

        String status = status(request.status());
        String resolutionComment = clean(request.resolutionComment());
        if ("RESUELTO".equals(status) && resolutionComment == null) {
            throw new IllegalArgumentException("Ingresa un comentario de cierre para resolver.");
        }

        jdbcClient.sql("""
                UPDATE community_post
                   SET status = ?,
                       resolution_comment = CASE WHEN ? = 'RESUELTO' THEN ? ELSE resolution_comment END,
                       resolved_by_user_id = CASE WHEN ? = 'RESUELTO' THEN ? ELSE NULL END,
                       resolved_at = CASE WHEN ? = 'RESUELTO' THEN NOW() ELSE NULL END,
                       updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .params(status, status, resolutionComment, status, currentUser.id(), status, id)
                .update();

        PostBase post = findPostBase(id);
        notifyUsers(
                findPostParticipantIdsExcept(id, currentUser.id()),
                "COMMUNITY_STATUS_CHANGED",
                "Estado actualizado",
                currentUser.displayName() + " cambio el estado a " + status + ": " + post.title(),
                id,
                null
        );
        return toPostDto(post, currentUser);
    }

    @Transactional
    public CommunityPostDto updatePinned(Long id, UpdatePinnedRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_FORUM_STATUS")) {
            throw new IllegalArgumentException("No tienes permiso para fijar publicaciones.");
        }
        jdbcClient.sql("""
                UPDATE community_post
                   SET pinned = ?, updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .params(Boolean.TRUE.equals(request.pinned()), id)
                .update();
        return findPostById(id, authentication);
    }

    @Transactional
    public void deletePost(Long id, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_FORUM_DELETE")) {
            throw new IllegalArgumentException("No tienes permiso para eliminar publicaciones.");
        }
        jdbcClient.sql("""
                UPDATE community_post
                   SET deleted_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .update();
    }

    @Transactional
    public CommunityPostDto createComment(Long postId, CreateCommentRequest request, List<MultipartFile> files, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        PostBase post = findPostBase(postId);
        if ("RESUELTO".equals(post.status())) {
            throw new IllegalArgumentException("No se puede comentar una publicacion resuelta.");
        }

        Long commentId = jdbcClient.sql("""
                INSERT INTO community_comment (post_id, author_user_id, content)
                VALUES (?, ?, ?)
                RETURNING id
                """)
                .params(postId, currentUser.id(), required(request.content(), "comentario"))
                .query(Long.class)
                .single();

        storeCommentFiles(postId, commentId, files);
        notifyUsers(
                findPostParticipantIdsExcept(postId, currentUser.id()),
                "COMMUNITY_COMMENT_CREATED",
                "Nuevo comentario",
                currentUser.displayName() + " comento en: " + post.title(),
                postId,
                commentId
        );

        return findPostById(postId, authentication);
    }

    @Transactional
    public CommunityPostDto updateComment(Long postId, Long commentId, UpdateCommentRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        PostBase post = findPostBase(postId);
        Long authorId = jdbcClient.sql("""
                SELECT author_user_id
                FROM community_comment
                WHERE id = ? AND post_id = ? AND deleted_at IS NULL
                """)
                .params(commentId, postId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado."));

        if (!canEditComment(authorId, post.status(), currentUser)) {
            throw new IllegalArgumentException("No tienes permiso para editar este comentario.");
        }

        jdbcClient.sql("""
                UPDATE community_comment
                   SET content = ?, updated_at = NOW()
                 WHERE id = ? AND post_id = ? AND deleted_at IS NULL
                """)
                .params(required(request.content(), "comentario"), commentId, postId)
                .update();

        return findPostById(postId, authentication);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_FORUM_DELETE")) {
            throw new IllegalArgumentException("No tienes permiso para eliminar comentarios.");
        }
        jdbcClient.sql("""
                UPDATE community_comment
                   SET deleted_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND post_id = ? AND deleted_at IS NULL
                """)
                .params(commentId, postId)
                .update();
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_FORUM_DELETE")) {
            throw new IllegalArgumentException("No tienes permiso para eliminar archivos.");
        }

        String key = jdbcClient.sql("""
                SELECT file_key
                FROM community_attachment
                WHERE id = ? AND deleted_at IS NULL
                """)
                .param(attachmentId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Archivo no encontrado."));

        jdbcClient.sql("""
                UPDATE community_attachment
                   SET deleted_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .param(attachmentId)
                .update();
        fileStorageService.deleteByKey(key);
    }

    public List<CommunityNotificationDto> findUnreadNotifications(Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        return jdbcClient.sql("""
                SELECT id, type, title, message, post_id, comment_id, read_at, created_at
                FROM community_notification
                WHERE user_id = ? AND read_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT 30
                """)
                .param(currentUser.id())
                .query((rs, rowNum) -> new CommunityNotificationDto(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("message"),
                        nullableLong(rs.getObject("post_id")),
                        nullableLong(rs.getObject("comment_id")),
                        rs.getTimestamp("read_at") != null,
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("read_at"))
                ))
                .list();
    }

    @Transactional
    public void markNotificationRead(Long id, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        jdbcClient.sql("""
                UPDATE community_notification
                   SET read_at = COALESCE(read_at, NOW())
                 WHERE id = ? AND user_id = ?
                """)
                .params(id, currentUser.id())
                .update();
    }

    public Long currentUserId(Authentication authentication) {
        return currentUser(authentication).id();
    }

    @Transactional
    public void markAllNotificationsRead(Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        jdbcClient.sql("""
                UPDATE community_notification
                   SET read_at = COALESCE(read_at, NOW())
                 WHERE user_id = ? AND read_at IS NULL
                """)
                .param(currentUser.id())
                .update();
    }

    @Transactional
    public CommunityPostDto react(Long postId, ReactionRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        findPostBase(postId);
        String reaction = reaction(request.reaction());
        jdbcClient.sql("""
                INSERT INTO community_post_reaction (post_id, user_id, reaction)
                VALUES (?, ?, ?)
                ON CONFLICT (post_id, user_id)
                DO UPDATE SET reaction = EXCLUDED.reaction, updated_at = NOW()
                """)
                .params(postId, currentUser.id(), reaction)
                .update();
        return findPostById(postId, authentication);
    }

    @Transactional
    public CommunityPostDto removeReaction(Long postId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        jdbcClient.sql("DELETE FROM community_post_reaction WHERE post_id = ? AND user_id = ?")
                .params(postId, currentUser.id())
                .update();
        return findPostById(postId, authentication);
    }

    @Transactional
    public CommunityPostDto markRead(Long postId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        findPostBase(postId);
        jdbcClient.sql("""
                INSERT INTO community_post_read (post_id, user_id)
                VALUES (?, ?)
                ON CONFLICT (post_id, user_id) DO NOTHING
                """)
                .params(postId, currentUser.id())
                .update();
        return findPostById(postId, authentication);
    }

    @Transactional
    public CommunityPostDto votePoll(Long postId, PollVoteRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        findPostBase(postId);

        PollMeta poll = jdbcClient.sql("""
                SELECT id, multiple, closes_at
                FROM community_poll
                WHERE post_id = ?
                """)
                .param(postId)
                .query((rs, rowNum) -> new PollMeta(
                        rs.getLong("id"),
                        rs.getBoolean("multiple"),
                        toLocalDateTime(rs.getTimestamp("closes_at"))
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("La publicacion no tiene encuesta."));

        if (poll.closesAt() != null && poll.closesAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La encuesta esta cerrada.");
        }

        List<Long> optionIds = request.optionIds() == null ? List.of() : request.optionIds();
        if (optionIds.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una opcion.");
        }
        if (!poll.multiple() && optionIds.size() > 1) {
            throw new IllegalArgumentException("Solo puedes elegir una opcion.");
        }

        jdbcClient.sql("DELETE FROM community_poll_vote WHERE poll_id = ? AND user_id = ?")
                .params(poll.id(), currentUser.id())
                .update();

        for (Long optionId : new LinkedHashSet<>(optionIds)) {
            int inserted = jdbcClient.sql("""
                    INSERT INTO community_poll_vote (poll_id, option_id, user_id)
                    SELECT ?, o.id, ?
                    FROM community_poll_option o
                    WHERE o.id = ? AND o.poll_id = ?
                    """)
                    .params(poll.id(), currentUser.id(), optionId, poll.id())
                    .update();
            if (inserted == 0) {
                throw new IllegalArgumentException("Opcion de encuesta invalida.");
            }
        }
        return findPostById(postId, authentication);
    }

    private CommunityGroupDto findGroupById(Long id) {
        return jdbcClient.sql("""
                SELECT id, name, description, color, icon, active, created_at, updated_at
                FROM community_group
                WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .query((rs, rowNum) -> new CommunityGroupDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("color"),
                        rs.getString("icon"),
                        rs.getBoolean("active"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));
    }

    private PostBase findPostBase(Long id) {
        return jdbcClient.sql("""
                SELECT cp.id,
                       cp.group_id,
                       cg.name AS group_name,
                       cg.color AS group_color,
                       cg.icon AS group_icon,
                       cp.type,
                       cp.status,
                       cp.title,
                       cp.content,
                       cp.pinned,
                       cp.resolution_comment,
                       cp.author_user_id,
                       u.username AS author_username,
                       u.first_name AS author_first_name,
                       u.last_name AS author_last_name,
                       cp.created_at,
                       cp.updated_at,
                       cp.resolved_at
                FROM community_post cp
                JOIN community_group cg ON cg.id = cp.group_id
                JOIN users u ON u.id = cp.author_user_id
                WHERE cp.id = ? AND cp.deleted_at IS NULL AND cg.deleted_at IS NULL
                """)
                .param(id)
                .query((rs, rowNum) -> new PostBase(
                        rs.getLong("id"),
                        rs.getLong("group_id"),
                        rs.getString("group_name"),
                        rs.getString("group_color"),
                        rs.getString("group_icon"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getBoolean("pinned"),
                        rs.getString("resolution_comment"),
                        userDto(
                                rs.getLong("author_user_id"),
                                rs.getString("author_username"),
                                rs.getString("author_first_name"),
                                rs.getString("author_last_name")
                        ),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at")),
                        toLocalDateTime(rs.getTimestamp("resolved_at"))
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Publicacion no encontrada."));
    }

    private CommunityPostDto toPostDto(PostBase base, CurrentUser currentUser) {
        List<CommunityCommentDto> comments = findComments(base.id(), base.status(), currentUser);
        List<CommunityReactionSummaryDto> reactions = findReactionSummary(base.id());
        return new CommunityPostDto(
                base.id(),
                base.groupId(),
                base.groupName(),
                base.groupColor(),
                base.groupIcon(),
                base.type(),
                base.status(),
                base.title(),
                base.content(),
                base.pinned(),
                base.resolutionComment(),
                base.author(),
                findProducts(base.id()),
                findAttachments(base.id(), null),
                comments,
                comments.size(),
                reactions,
                myReaction(base.id(), currentUser.id()),
                reactionTotal(reactions),
                readStats(base.id(), audienceCount(), currentUser),
                findPoll(base.id(), currentUser),
                canEditPost(base.author().id(), base.status(), currentUser),
                currentUser.has("MANAGE_FORUM_DELETE"),
                base.createdAt(),
                base.updatedAt(),
                base.resolvedAt()
        );
    }

    private List<CommunityCommentDto> findComments(Long postId, String postStatus, CurrentUser currentUser) {
        return jdbcClient.sql("""
                SELECT cc.id,
                       cc.post_id,
                       cc.content,
                       cc.author_user_id,
                       u.username,
                       u.first_name,
                       u.last_name,
                       cc.created_at,
                       cc.updated_at
                FROM community_comment cc
                JOIN users u ON u.id = cc.author_user_id
                WHERE cc.post_id = ? AND cc.deleted_at IS NULL
                ORDER BY cc.created_at ASC, cc.id ASC
                """)
                .param(postId)
                .query((rs, rowNum) -> {
                    Long commentId = rs.getLong("id");
                    Long authorId = rs.getLong("author_user_id");
                    return new CommunityCommentDto(
                            commentId,
                            rs.getLong("post_id"),
                            rs.getString("content"),
                            userDto(
                                    authorId,
                                    rs.getString("username"),
                                    rs.getString("first_name"),
                                    rs.getString("last_name")
                            ),
                            findAttachments(null, commentId),
                            canEditComment(authorId, postStatus, currentUser),
                            currentUser.has("MANAGE_FORUM_DELETE"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at"))
                    );
                })
                .list();
    }

    private List<CommunityAttachmentDto> findAttachments(Long postId, Long commentId) {
        String sql;
        Object param;
        if (postId != null) {
            sql = """
                    SELECT id, post_id, comment_id, file_name, original_name, content_type, media_type, size_bytes, file_key, created_at
                    FROM community_attachment
                    WHERE post_id = ? AND comment_id IS NULL AND deleted_at IS NULL
                    ORDER BY id ASC
                    """;
            param = postId;
        } else {
            sql = """
                    SELECT id, post_id, comment_id, file_name, original_name, content_type, media_type, size_bytes, file_key, created_at
                    FROM community_attachment
                    WHERE comment_id = ? AND deleted_at IS NULL
                    ORDER BY id ASC
                    """;
            param = commentId;
        }

        return jdbcClient.sql(sql)
                .param(param)
                .query((rs, rowNum) -> new CommunityAttachmentDto(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("post_id")),
                        nullableLong(rs.getObject("comment_id")),
                        rs.getString("file_name"),
                        rs.getString("original_name"),
                        rs.getString("content_type"),
                        rs.getString("media_type"),
                        rs.getLong("size_bytes"),
                        toPublicFileUrl(rs.getString("file_key")),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ))
                .list();
    }

    private List<CommunityProductDto> findProducts(Long postId) {
        return jdbcClient.sql("""
                SELECT p.id, p.sku, p.name, p.brand, p.model, p.category
                FROM community_post_product cpp
                JOIN product p ON p.id = cpp.product_id
                WHERE cpp.post_id = ?
                ORDER BY p.name ASC
                """)
                .param(postId)
                .query((rs, rowNum) -> new CommunityProductDto(
                        rs.getLong("id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("category")
                ))
                .list();
    }

    private void replaceProducts(Long postId, List<Long> productIds) {
        jdbcClient.sql("DELETE FROM community_post_product WHERE post_id = ?")
                .param(postId)
                .update();
        if (productIds == null || productIds.isEmpty()) return;

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(productIds);
        for (Long productId : uniqueIds) {
            int inserted = jdbcClient.sql("""
                    INSERT INTO community_post_product (post_id, product_id)
                    SELECT ?, p.id
                    FROM product p
                    WHERE p.id = ?
                    """)
                    .params(postId, productId)
                    .update();
            if (inserted == 0) {
                throw new IllegalArgumentException("Producto no encontrado: " + productId);
            }
        }
    }

    private List<CommunityReactionSummaryDto> findReactionSummary(Long postId) {
        return jdbcClient.sql("""
                SELECT reaction, COUNT(*)::int AS total
                FROM community_post_reaction
                WHERE post_id = ?
                GROUP BY reaction
                """)
                .param(postId)
                .query((rs, rowNum) -> new CommunityReactionSummaryDto(
                        rs.getString("reaction"),
                        rs.getInt("total")
                ))
                .list();
    }

    private String myReaction(Long postId, Long userId) {
        return jdbcClient.sql("""
                SELECT reaction
                FROM community_post_reaction
                WHERE post_id = ? AND user_id = ?
                """)
                .params(postId, userId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private int reactionTotal(List<CommunityReactionSummaryDto> reactions) {
        return reactions.stream().mapToInt(CommunityReactionSummaryDto::count).sum();
    }

    private CommunityReadStatsDto readStats(Long postId, int audience, CurrentUser currentUser) {
        int readCount = jdbcClient.sql("SELECT COUNT(*)::int FROM community_post_read WHERE post_id = ?")
                .param(postId)
                .query(Integer.class)
                .single();
        boolean readByMe = Boolean.TRUE.equals(jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM community_post_read WHERE post_id = ? AND user_id = ?
                )
                """)
                .params(postId, currentUser.id())
                .query(Boolean.class)
                .single());
        int percent = audience <= 0 ? 0 : (int) Math.round(readCount * 100.0 / audience);
        if (percent > 100) percent = 100;
        return new CommunityReadStatsDto(readCount, audience, percent, readByMe);
    }

    private int audienceCount() {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(DISTINCT u.id)::int
                FROM users u
                LEFT JOIN user_permissions up ON up.user_id = u.id
                LEFT JOIN permissions upm ON upm.id = up.permission_id
                LEFT JOIN role_permissions rp ON rp.role_id = u.role_id
                LEFT JOIN permissions rpm ON rpm.id = rp.permission_id
                WHERE u.enabled = TRUE
                  AND (upm.name = 'MANAGE_FORUMS_VIEW' OR rpm.name = 'MANAGE_FORUMS_VIEW')
                """)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    private CommunityPollDto findPoll(Long postId, CurrentUser currentUser) {
        PollMeta poll = jdbcClient.sql("""
                SELECT cp.id, cp.question, cp.multiple, cp.closes_at
                FROM community_poll cp
                WHERE cp.post_id = ?
                """)
                .param(postId)
                .query((rs, rowNum) -> new PollMeta(
                        rs.getLong("id"),
                        rs.getBoolean("multiple"),
                        toLocalDateTime(rs.getTimestamp("closes_at")),
                        rs.getString("question")
                ))
                .optional()
                .orElse(null);
        if (poll == null) return null;

        List<CommunityPollOptionDto> options = jdbcClient.sql("""
                SELECT o.id, o.label, o.position,
                       COUNT(v.id)::int AS votes,
                       BOOL_OR(v.user_id = ?) AS voted_by_me
                FROM community_poll_option o
                LEFT JOIN community_poll_vote v ON v.option_id = o.id
                WHERE o.poll_id = ?
                GROUP BY o.id, o.label, o.position
                ORDER BY o.position ASC, o.id ASC
                """)
                .params(currentUser.id(), poll.id())
                .query((rs, rowNum) -> new CommunityPollOptionDto(
                        rs.getLong("id"),
                        rs.getString("label"),
                        rs.getInt("position"),
                        rs.getInt("votes"),
                        rs.getBoolean("voted_by_me")
                ))
                .list();

        int totalVoters = jdbcClient.sql("SELECT COUNT(DISTINCT user_id)::int FROM community_poll_vote WHERE poll_id = ?")
                .param(poll.id())
                .query(Integer.class)
                .single();
        boolean votedByMe = options.stream().anyMatch(CommunityPollOptionDto::votedByMe);
        boolean closed = poll.closesAt() != null && poll.closesAt().isBefore(LocalDateTime.now());

        return new CommunityPollDto(
                poll.id(),
                poll.question(),
                poll.multiple(),
                poll.closesAt(),
                closed,
                totalVoters,
                options,
                votedByMe
        );
    }

    private void createPoll(Long postId, PollInput poll) {
        if (poll == null) return;
        String question = clean(poll.question());
        if (question == null) return;

        List<String> cleanOptions = new ArrayList<>();
        if (poll.options() != null) {
            for (String option : poll.options()) {
                String cleaned = clean(option);
                if (cleaned != null) cleanOptions.add(cleaned);
            }
        }
        if (cleanOptions.size() < 2) {
            throw new IllegalArgumentException("La encuesta requiere al menos 2 opciones.");
        }

        Long pollId = jdbcClient.sql("""
                INSERT INTO community_poll (post_id, question, multiple)
                VALUES (?, ?, ?)
                RETURNING id
                """)
                .params(postId, question, Boolean.TRUE.equals(poll.multiple()))
                .query(Long.class)
                .single();

        int position = 0;
        for (String option : cleanOptions) {
            jdbcClient.sql("""
                    INSERT INTO community_poll_option (poll_id, label, position)
                    VALUES (?, ?, ?)
                    """)
                    .params(pollId, option, position++)
                    .update();
        }
    }

    private void storePostFiles(Long postId, List<MultipartFile> files) {
        for (MultipartFile file : safeFiles(files)) {
            StoredCommunityFile stored = fileStorageService.storePostFile(postId, file);
            insertAttachment(postId, null, stored);
        }
    }

    private void storeCommentFiles(Long postId, Long commentId, List<MultipartFile> files) {
        for (MultipartFile file : safeFiles(files)) {
            StoredCommunityFile stored = fileStorageService.storeCommentFile(postId, commentId, file);
            insertAttachment(postId, commentId, stored);
        }
    }

    private void insertAttachment(Long postId, Long commentId, StoredCommunityFile file) {
        jdbcClient.sql("""
                INSERT INTO community_attachment (
                    post_id, comment_id, file_key, file_name, original_name,
                    content_type, media_type, size_bytes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(
                        postId,
                        commentId,
                        file.key(),
                        file.fileName(),
                        file.originalName(),
                        file.contentType(),
                        file.mediaType(),
                        file.sizeBytes()
                )
                .update();
    }

    private List<MultipartFile> safeFiles(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void notifyUsers(Collection<Long> userIds, String type, String title, String message, Long postId, Long commentId) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(userIds);
        for (Long userId : uniqueIds) {
            NotificationRow row = jdbcClient.sql("""
                    INSERT INTO community_notification (user_id, type, title, message, post_id, comment_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id, created_at
                    """)
                    .params(userId, type, title, message, postId, commentId)
                    .query((rs, rowNum) -> new NotificationRow(
                            rs.getLong("id"),
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    ))
                    .single();

            CommunityNotificationDto dto = new CommunityNotificationDto(
                    row.id(),
                    type,
                    title,
                    message,
                    postId,
                    commentId,
                    false,
                    row.createdAt(),
                    null
            );
            sseService.push(userId, dto);
        }
    }

    private List<Long> findForumViewUserIdsExcept(Long actorUserId) {
        return jdbcClient.sql("""
                SELECT DISTINCT u.id
                FROM users u
                LEFT JOIN user_permissions up ON up.user_id = u.id
                LEFT JOIN permissions upm ON upm.id = up.permission_id
                LEFT JOIN role_permissions rp ON rp.role_id = u.role_id
                LEFT JOIN permissions rpm ON rpm.id = rp.permission_id
                WHERE u.enabled = TRUE
                  AND u.id <> ?
                  AND (upm.name = 'MANAGE_FORUMS_VIEW' OR rpm.name = 'MANAGE_FORUMS_VIEW')
                """)
                .param(actorUserId)
                .query(Long.class)
                .list();
    }

    private List<Long> findPostParticipantIdsExcept(Long postId, Long actorUserId) {
        return jdbcClient.sql("""
                SELECT DISTINCT user_id
                FROM (
                    SELECT author_user_id AS user_id
                    FROM community_post
                    WHERE id = ?
                    UNION
                    SELECT author_user_id AS user_id
                    FROM community_comment
                    WHERE post_id = ? AND deleted_at IS NULL
                ) participants
                WHERE user_id <> ?
                """)
                .params(postId, postId, actorUserId)
                .query(Long.class)
                .list();
    }

    private void validatePost(Long groupId, String type, String title, String content, List<Long> productIds) {
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException("Selecciona un grupo.");
        }
        type(type);
        required(title, "titulo");
        required(content, "contenido");
    }

    private void ensureGroupExists(Long groupId) {
        Boolean exists = jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_group
                    WHERE id = ? AND active = TRUE AND deleted_at IS NULL
                )
                """)
                .param(groupId)
                .query(Boolean.class)
                .single();
        if (!Boolean.TRUE.equals(exists)) {
            throw new IllegalArgumentException("Grupo no encontrado o inactivo.");
        }
    }

    private boolean canEditPost(Long authorId, String status, CurrentUser currentUser) {
        if (currentUser.has("MANAGE_FORUM_STATUS") || currentUser.has("MANAGE_FORUM_DELETE")) return true;
        return Objects.equals(authorId, currentUser.id()) && !"RESUELTO".equals(status);
    }

    private boolean canEditComment(Long authorId, String postStatus, CurrentUser currentUser) {
        if (currentUser.has("MANAGE_FORUM_DELETE")) return true;
        return Objects.equals(authorId, currentUser.id()) && !"RESUELTO".equals(postStatus);
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Usuario autenticado no encontrado.");
        }
        String username = authentication.getName();
        CurrentUserInfo info = jdbcClient.sql("""
                SELECT id, username, first_name, last_name
                FROM users
                WHERE username = ?
                """)
                .param(username)
                .query((rs, rowNum) -> new CurrentUserInfo(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name")
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        Set<String> permissions = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            permissions.add(authority.getAuthority());
        }
        return new CurrentUser(
                info.id(),
                info.username(),
                info.firstName(),
                info.lastName(),
                permissions
        );
    }

    private static CommunityUserDto userDto(Long id, String username, String firstName, String lastName) {
        String display = (String.valueOf(firstName == null ? "" : firstName).trim() + " " +
                String.valueOf(lastName == null ? "" : lastName).trim()).trim();
        if (display.isEmpty()) display = username;
        return new CommunityUserDto(id, username, firstName, lastName, display);
    }

    private String toPublicFileUrl(String key) {
        if (key == null || key.isBlank()) return null;
        String base = publicPath.endsWith("/") ? publicPath.substring(0, publicPath.length() - 1) : publicPath;
        String path = base + "/" + key.replaceFirst("^/+", "");
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path(path.startsWith("/") ? path : "/" + path)
                .toUriString();
    }

    private static String type(String value) {
        String clean = required(value, "tipo").toUpperCase(Locale.ROOT);
        if (!TYPES.contains(clean)) throw new IllegalArgumentException("Tipo de publicacion invalido.");
        return clean;
    }

    private static String status(String value) {
        String clean = required(value, "estado").toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(clean)) throw new IllegalArgumentException("Estado de publicacion invalido.");
        return clean;
    }

    private static String reaction(String value) {
        String clean = required(value, "reaccion").toUpperCase(Locale.ROOT);
        if (!REACTIONS.contains(clean)) throw new IllegalArgumentException("Reaccion invalida.");
        return clean;
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

    private static String preview(String value) {
        String clean = String.valueOf(value == null ? "" : value).trim();
        if (clean.length() <= 180) return clean;
        return clean.substring(0, 180) + "...";
    }

    private static Long nullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record PostBase(
            Long id,
            Long groupId,
            String groupName,
            String groupColor,
            String groupIcon,
            String type,
            String status,
            String title,
            String content,
            Boolean pinned,
            String resolutionComment,
            CommunityUserDto author,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime resolvedAt
    ) {
    }

    private record CurrentUserInfo(Long id, String username, String firstName, String lastName) {
    }

    private record NotificationRow(Long id, LocalDateTime createdAt) {
    }

    private record PollMeta(Long id, boolean multiple, LocalDateTime closesAt, String question) {
        PollMeta(Long id, boolean multiple, LocalDateTime closesAt) {
            this(id, multiple, closesAt, null);
        }
    }

    private record CurrentUser(
            Long id,
            String username,
            String firstName,
            String lastName,
            Set<String> permissions
    ) {
        boolean has(String permission) {
            return permissions.contains(permission);
        }

        String displayName() {
            String display = (String.valueOf(firstName == null ? "" : firstName).trim() + " " +
                    String.valueOf(lastName == null ? "" : lastName).trim()).trim();
            return display.isEmpty() ? username : display;
        }
    }
}
