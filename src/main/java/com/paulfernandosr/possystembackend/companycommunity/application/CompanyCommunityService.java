package com.paulfernandosr.possystembackend.companycommunity.application;

import com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.input.dto.CompanyCommunityDtos.*;
import com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.output.CompanyCommunityFileStorageService;
import com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.output.CompanyCommunityFileStorageService.StoredCompanyCommunityFile;
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
public class CompanyCommunityService {

    private static final Set<String> TYPES = Set.of("HISTORIA", "COMPRAS", "PROCESO", "NOTICIA", "VIDEO");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

    private final JdbcClient jdbcClient;
    private final CompanyCommunityFileStorageService fileStorageService;

    @Value("${app.files.company-community-public-path:/files/company-community}")
    private String publicPath;

    public List<CompanyCommunityCategoryDto> findCategories() {
        return jdbcClient.sql("""
                SELECT id, name, description, color, icon, active, created_at, updated_at
                FROM company_community_category
                WHERE deleted_at IS NULL
                ORDER BY active DESC, name ASC
                """)
                .query((rs, rowNum) -> new CompanyCommunityCategoryDto(
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
    public CompanyCommunityCategoryDto createCategory(CategoryRequest request) {
        Long id = jdbcClient.sql("""
                INSERT INTO company_community_category (name, description, color, icon, active)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """)
                .params(
                        required(request.name(), "nombre"),
                        clean(request.description()),
                        clean(request.color()),
                        clean(request.icon()),
                        request.active() == null || request.active()
                )
                .query(Long.class)
                .single();
        return findCategoryById(id);
    }

    @Transactional
    public CompanyCommunityCategoryDto updateCategory(Long id, CategoryRequest request) {
        int updated = jdbcClient.sql("""
                UPDATE company_community_category
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
        if (updated == 0) throw new IllegalArgumentException("Categoria no encontrada.");
        return findCategoryById(id);
    }

    @Transactional
    public void deleteCategory(Long id) {
        int updated = jdbcClient.sql("""
                UPDATE company_community_category
                   SET deleted_at = NOW(), active = FALSE, updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .update();
        if (updated == 0) throw new IllegalArgumentException("Categoria no encontrada.");
    }

    public List<CompanyCommunityPostSummaryDto> findPosts(CompanyCommunityPostFilters filters, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        boolean canManage = currentUser.has("MANAGE_COMPANY_COMMUNITY_POSTS");
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT cp.id,
                       cp.category_id,
                       cc.name AS category_name,
                       cc.color AS category_color,
                       cc.icon AS category_icon,
                       cp.type,
                       cp.status,
                       cp.title,
                       cp.summary,
                       cp.pinned,
                       cp.published_at,
                       cp.author_user_id,
                       u.username AS author_username,
                       u.first_name AS author_first_name,
                       u.last_name AS author_last_name,
                       cp.created_at,
                       cp.updated_at
                FROM company_community_post cp
                JOIN company_community_category cc ON cc.id = cp.category_id
                JOIN users u ON u.id = cp.author_user_id
                WHERE cp.deleted_at IS NULL
                  AND cc.deleted_at IS NULL
                """);

        if (!canManage) {
            sql.append(" AND cp.status = 'PUBLISHED' ");
        } else if (clean(filters.status()) != null && !"ALL".equalsIgnoreCase(clean(filters.status()))) {
            sql.append(" AND cp.status = ? ");
            params.add(status(filters.status()));
        }
        if (clean(filters.query()) != null) {
            String q = "%" + clean(filters.query()).toUpperCase(Locale.ROOT) + "%";
            sql.append("""
                      AND (
                          UPPER(cp.title) LIKE ?
                          OR UPPER(COALESCE(cp.summary, '')) LIKE ?
                          OR UPPER(cp.content) LIKE ?
                          OR UPPER(cc.name) LIKE ?
                      )
                    """);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (filters.categoryId() != null) {
            sql.append(" AND cp.category_id = ? ");
            params.add(filters.categoryId());
        }
        if (clean(filters.type()) != null && !"ALL".equalsIgnoreCase(clean(filters.type()))) {
            sql.append(" AND cp.type = ? ");
            params.add(type(filters.type()));
        }
        if (filters.pinned() != null) {
            sql.append(" AND cp.pinned = ? ");
            params.add(filters.pinned());
        }
        if (filters.dateFrom() != null) {
            sql.append(" AND COALESCE(cp.published_at, cp.created_at)::date >= ? ");
            params.add(filters.dateFrom());
        }
        if (filters.dateTo() != null) {
            sql.append(" AND COALESCE(cp.published_at, cp.created_at)::date <= ? ");
            params.add(filters.dateTo());
        }

        int limit = filters.limit() == null ? 10 : Math.max(1, Math.min(filters.limit(), 50));
        long offset = filters.offset() == null ? 0L : Math.max(0L, filters.offset().longValue());
        sql.append("""
                ORDER BY cp.pinned DESC,
                         COALESCE(cp.published_at, cp.updated_at) DESC,
                         cp.id DESC
                LIMIT ? OFFSET ?
                """);
        params.add(limit);
        params.add(offset);

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query((rs, rowNum) -> {
                    Long postId = rs.getLong("id");
                    Long authorId = rs.getLong("author_user_id");
                    return new CompanyCommunityPostSummaryDto(
                            postId,
                            rs.getLong("category_id"),
                            rs.getString("category_name"),
                            rs.getString("category_color"),
                            rs.getString("category_icon"),
                            rs.getString("type"),
                            rs.getString("status"),
                            rs.getString("title"),
                            rs.getString("summary"),
                            rs.getBoolean("pinned"),
                            toLocalDateTime(rs.getTimestamp("published_at")),
                            userDto(authorId, rs.getString("author_username"), rs.getString("author_first_name"), rs.getString("author_last_name")),
                            findAttachments(postId),
                            canEditPost(authorId, currentUser),
                            currentUser.has("MANAGE_COMPANY_COMMUNITY_DELETE"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at"))
                    );
                })
                .list();
    }

    public CompanyCommunityPostDto findPostById(Long id, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        PostBase base = findPostBase(id);
        if (!"PUBLISHED".equals(base.status()) && !currentUser.has("MANAGE_COMPANY_COMMUNITY_POSTS")) {
            throw new IllegalArgumentException("No tienes permiso para ver esta publicacion.");
        }
        return toPostDto(base, currentUser);
    }

    @Transactional
    public CompanyCommunityPostDto createPost(PostRequest request, List<MultipartFile> files, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        validatePost(request);
        ensureCategoryExists(request.categoryId());

        String requestedStatus = statusOrDefault(request.status());
        Long id = jdbcClient.sql("""
                INSERT INTO company_community_post (
                    category_id, type, status, title, summary, content, pinned,
                    published_at, author_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'PUBLISHED' THEN NOW() ELSE NULL END, ?)
                RETURNING id
                """)
                .params(
                        request.categoryId(),
                        type(request.type()),
                        requestedStatus,
                        required(request.title(), "titulo"),
                        clean(request.summary()),
                        required(request.content(), "contenido"),
                        Boolean.TRUE.equals(request.pinned()),
                        requestedStatus,
                        currentUser.id()
                )
                .query(Long.class)
                .single();

        storePostFiles(id, files);
        return findPostById(id, authentication);
    }

    @Transactional
    public CompanyCommunityPostDto updatePost(Long id, PostRequest request, List<MultipartFile> files, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        PostBase current = findPostBase(id);
        if (!canEditPost(current.author().id(), currentUser)) {
            throw new IllegalArgumentException("No tienes permiso para editar esta publicacion.");
        }
        validatePost(request);
        ensureCategoryExists(request.categoryId());
        String requestedStatus = statusOrDefault(request.status());

        jdbcClient.sql("""
                UPDATE company_community_post
                   SET category_id = ?,
                       type = ?,
                       status = ?,
                       title = ?,
                       summary = ?,
                       content = ?,
                       pinned = ?,
                       published_at = CASE
                           WHEN ? = 'PUBLISHED' AND published_at IS NULL THEN NOW()
                           WHEN ? <> 'PUBLISHED' THEN NULL
                           ELSE published_at
                       END,
                       updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .params(
                        request.categoryId(),
                        type(request.type()),
                        requestedStatus,
                        required(request.title(), "titulo"),
                        clean(request.summary()),
                        required(request.content(), "contenido"),
                        Boolean.TRUE.equals(request.pinned()),
                        requestedStatus,
                        requestedStatus,
                        id
                )
                .update();

        storePostFiles(id, files);
        return findPostById(id, authentication);
    }

    @Transactional
    public CompanyCommunityPostDto updateStatus(Long id, UpdateStatusRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_COMPANY_COMMUNITY_POSTS")) {
            throw new IllegalArgumentException("No tienes permiso para cambiar el estado.");
        }
        String nextStatus = status(request.status());
        jdbcClient.sql("""
                UPDATE company_community_post
                   SET status = ?,
                       published_at = CASE
                           WHEN ? = 'PUBLISHED' AND published_at IS NULL THEN NOW()
                           WHEN ? <> 'PUBLISHED' THEN NULL
                           ELSE published_at
                       END,
                       updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .params(nextStatus, nextStatus, nextStatus, id)
                .update();
        return findPostById(id, authentication);
    }

    @Transactional
    public CompanyCommunityPostDto updatePinned(Long id, UpdatePinnedRequest request, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_COMPANY_COMMUNITY_POSTS")) {
            throw new IllegalArgumentException("No tienes permiso para destacar publicaciones.");
        }
        jdbcClient.sql("""
                UPDATE company_community_post
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
        if (!currentUser.has("MANAGE_COMPANY_COMMUNITY_DELETE")) {
            throw new IllegalArgumentException("No tienes permiso para eliminar publicaciones.");
        }
        jdbcClient.sql("""
                UPDATE company_community_post
                   SET deleted_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .update();
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        if (!currentUser.has("MANAGE_COMPANY_COMMUNITY_DELETE")) {
            throw new IllegalArgumentException("No tienes permiso para eliminar archivos.");
        }
        String fileKey = jdbcClient.sql("""
                SELECT file_key
                FROM company_community_attachment
                WHERE id = ? AND deleted_at IS NULL
                """)
                .param(attachmentId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Archivo no encontrado."));

        jdbcClient.sql("""
                UPDATE company_community_attachment
                   SET deleted_at = NOW()
                 WHERE id = ? AND deleted_at IS NULL
                """)
                .param(attachmentId)
                .update();
        fileStorageService.deleteByKey(fileKey);
    }

    private CompanyCommunityCategoryDto findCategoryById(Long id) {
        return jdbcClient.sql("""
                SELECT id, name, description, color, icon, active, created_at, updated_at
                FROM company_community_category
                WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .query((rs, rowNum) -> new CompanyCommunityCategoryDto(
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
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada."));
    }

    private PostBase findPostBase(Long id) {
        return jdbcClient.sql("""
                SELECT cp.id,
                       cp.category_id,
                       cc.name AS category_name,
                       cc.color AS category_color,
                       cc.icon AS category_icon,
                       cp.type,
                       cp.status,
                       cp.title,
                       cp.summary,
                       cp.content,
                       cp.pinned,
                       cp.published_at,
                       cp.author_user_id,
                       u.username AS author_username,
                       u.first_name AS author_first_name,
                       u.last_name AS author_last_name,
                       cp.created_at,
                       cp.updated_at
                FROM company_community_post cp
                JOIN company_community_category cc ON cc.id = cp.category_id
                JOIN users u ON u.id = cp.author_user_id
                WHERE cp.id = ? AND cp.deleted_at IS NULL AND cc.deleted_at IS NULL
                """)
                .param(id)
                .query((rs, rowNum) -> new PostBase(
                        rs.getLong("id"),
                        rs.getLong("category_id"),
                        rs.getString("category_name"),
                        rs.getString("category_color"),
                        rs.getString("category_icon"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("content"),
                        rs.getBoolean("pinned"),
                        toLocalDateTime(rs.getTimestamp("published_at")),
                        userDto(
                                rs.getLong("author_user_id"),
                                rs.getString("author_username"),
                                rs.getString("author_first_name"),
                                rs.getString("author_last_name")
                        ),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Publicacion no encontrada."));
    }

    private CompanyCommunityPostDto toPostDto(PostBase base, CurrentUser currentUser) {
        return new CompanyCommunityPostDto(
                base.id(),
                base.categoryId(),
                base.categoryName(),
                base.categoryColor(),
                base.categoryIcon(),
                base.type(),
                base.status(),
                base.title(),
                base.summary(),
                base.content(),
                base.pinned(),
                base.publishedAt(),
                base.author(),
                findAttachments(base.id()),
                canEditPost(base.author().id(), currentUser),
                currentUser.has("MANAGE_COMPANY_COMMUNITY_DELETE"),
                base.createdAt(),
                base.updatedAt()
        );
    }

    private List<CompanyCommunityAttachmentDto> findAttachments(Long postId) {
        return jdbcClient.sql("""
                SELECT id, post_id, file_name, original_name, content_type, media_type,
                       size_bytes, position, file_key, created_at
                FROM company_community_attachment
                WHERE post_id = ? AND deleted_at IS NULL
                ORDER BY position ASC, id ASC
                """)
                .param(postId)
                .query((rs, rowNum) -> new CompanyCommunityAttachmentDto(
                        rs.getLong("id"),
                        rs.getLong("post_id"),
                        rs.getString("file_name"),
                        rs.getString("original_name"),
                        rs.getString("content_type"),
                        rs.getString("media_type"),
                        rs.getLong("size_bytes"),
                        rs.getInt("position"),
                        toPublicFileUrl(rs.getString("file_key")),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ))
                .list();
    }

    private void storePostFiles(Long postId, List<MultipartFile> files) {
        int nextPosition = jdbcClient.sql("""
                SELECT COALESCE(MAX(position), 0) + 1
                FROM company_community_attachment
                WHERE post_id = ? AND deleted_at IS NULL
                """)
                .param(postId)
                .query(Integer.class)
                .single();

        for (MultipartFile file : safeFiles(files)) {
            StoredCompanyCommunityFile stored = fileStorageService.storePostFile(postId, file);
            jdbcClient.sql("""
                    INSERT INTO company_community_attachment (
                        post_id, file_key, file_name, original_name,
                        content_type, media_type, size_bytes, position
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .params(
                            postId,
                            stored.key(),
                            stored.fileName(),
                            stored.originalName(),
                            stored.contentType(),
                            stored.mediaType(),
                            stored.sizeBytes(),
                            nextPosition++
                    )
                    .update();
        }
    }

    private void validatePost(PostRequest request) {
        if (request.categoryId() == null || request.categoryId() <= 0) throw new IllegalArgumentException("Selecciona una categoria.");
        type(request.type());
        statusOrDefault(request.status());
        required(request.title(), "titulo");
        required(request.content(), "contenido");
    }

    private void ensureCategoryExists(Long categoryId) {
        Boolean exists = jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM company_community_category
                    WHERE id = ? AND active = TRUE AND deleted_at IS NULL
                )
                """)
                .param(categoryId)
                .query(Boolean.class)
                .single();
        if (!Boolean.TRUE.equals(exists)) throw new IllegalArgumentException("Categoria no encontrada o inactiva.");
    }

    private boolean canEditPost(Long authorId, CurrentUser currentUser) {
        return currentUser.has("MANAGE_COMPANY_COMMUNITY_POSTS") || Objects.equals(authorId, currentUser.id());
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Usuario autenticado no encontrado.");
        }
        CurrentUserInfo info = jdbcClient.sql("""
                SELECT id, username, first_name, last_name
                FROM users
                WHERE username = ?
                """)
                .param(authentication.getName())
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
        return new CurrentUser(info.id(), info.username(), info.firstName(), info.lastName(), permissions);
    }

    private static CompanyCommunityUserDto userDto(Long id, String username, String firstName, String lastName) {
        String display = (String.valueOf(firstName == null ? "" : firstName).trim() + " " +
                String.valueOf(lastName == null ? "" : lastName).trim()).trim();
        if (display.isEmpty()) display = username;
        return new CompanyCommunityUserDto(id, username, firstName, lastName, display);
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

    private static String statusOrDefault(String value) {
        return clean(value) == null ? "DRAFT" : status(value);
    }

    private static String status(String value) {
        String clean = required(value, "estado").toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(clean)) throw new IllegalArgumentException("Estado de publicacion invalido.");
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

    private static List<MultipartFile> safeFiles(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record PostBase(
            Long id,
            Long categoryId,
            String categoryName,
            String categoryColor,
            String categoryIcon,
            String type,
            String status,
            String title,
            String summary,
            String content,
            Boolean pinned,
            LocalDateTime publishedAt,
            CompanyCommunityUserDto author,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    private record CurrentUserInfo(Long id, String username, String firstName, String lastName) {
    }

    private record CurrentUser(Long id, String username, String firstName, String lastName, Set<String> permissions) {
        boolean has(String permission) {
            return permissions.contains(permission);
        }
    }
}
