package com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.input.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CompanyCommunityDtos {
    private CompanyCommunityDtos() {
    }

    public record CompanyCommunityUserDto(
            Long id,
            String username,
            String firstName,
            String lastName,
            String displayName
    ) {
    }

    public record CompanyCommunityCategoryDto(
            Long id,
            String name,
            String description,
            String color,
            String icon,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CompanyCommunityAttachmentDto(
            Long id,
            Long postId,
            String fileName,
            String originalName,
            String contentType,
            String mediaType,
            Long sizeBytes,
            Integer position,
            String fileUrl,
            LocalDateTime createdAt
    ) {
    }

    public record CompanyCommunityPostSummaryDto(
            Long id,
            Long categoryId,
            String categoryName,
            String categoryColor,
            String categoryIcon,
            String type,
            String status,
            String title,
            String summary,
            Boolean pinned,
            LocalDateTime publishedAt,
            CompanyCommunityUserDto author,
            List<CompanyCommunityAttachmentDto> attachments,
            Boolean editableByMe,
            Boolean deletableByMe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CompanyCommunityPostDto(
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
            List<CompanyCommunityAttachmentDto> attachments,
            Boolean editableByMe,
            Boolean deletableByMe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CompanyCommunityPostFilters(
            String query,
            Long categoryId,
            String type,
            String status,
            Boolean pinned,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer limit,
            Integer offset
    ) {
    }

    public record CategoryRequest(
            String name,
            String description,
            String color,
            String icon,
            Boolean active
    ) {
    }

    public record PostRequest(
            Long categoryId,
            String type,
            String status,
            String title,
            String summary,
            String content,
            Boolean pinned
    ) {
    }

    public record UpdateStatusRequest(String status) {
    }

    public record UpdatePinnedRequest(Boolean pinned) {
    }
}
