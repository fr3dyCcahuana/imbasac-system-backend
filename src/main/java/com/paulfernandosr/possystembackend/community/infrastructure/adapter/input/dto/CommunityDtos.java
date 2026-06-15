package com.paulfernandosr.possystembackend.community.infrastructure.adapter.input.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {
    }

    public record CommunityUserDto(
            Long id,
            String username,
            String firstName,
            String lastName,
            String displayName
    ) {
    }

    public record CommunityGroupDto(
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

    public record CommunityProductDto(
            Long id,
            String sku,
            String name,
            String brand,
            String model,
            String category
    ) {
    }

    public record CommunityAttachmentDto(
            Long id,
            Long postId,
            Long commentId,
            String fileName,
            String originalName,
            String contentType,
            String mediaType,
            Long sizeBytes,
            String fileUrl,
            LocalDateTime createdAt
    ) {
    }

    public record CommunityCommentDto(
            Long id,
            Long postId,
            String content,
            CommunityUserDto author,
            List<CommunityAttachmentDto> attachments,
            Boolean editableByMe,
            Boolean deletableByMe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    // ---- Reacciones corporativas ----
    public record CommunityReactionSummaryDto(
            String reaction,
            int count
    ) {
    }

    // ---- Confirmacion de lectura ----
    public record CommunityReadStatsDto(
            int readCount,
            int audienceCount,
            int readPercent,
            boolean readByMe
    ) {
    }

    // ---- Encuestas ----
    public record CommunityPollOptionDto(
            Long id,
            String label,
            int position,
            int votes,
            boolean votedByMe
    ) {
    }

    public record CommunityPollDto(
            Long id,
            String question,
            boolean multiple,
            LocalDateTime closesAt,
            boolean closed,
            int totalVoters,
            List<CommunityPollOptionDto> options,
            boolean votedByMe
    ) {
    }

    public record CommunityPostDto(
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
            List<CommunityProductDto> products,
            List<CommunityAttachmentDto> attachments,
            List<CommunityCommentDto> comments,
            Integer commentCount,
            List<CommunityReactionSummaryDto> reactions,
            String myReaction,
            int reactionCount,
            CommunityReadStatsDto read,
            CommunityPollDto poll,
            Boolean editableByMe,
            Boolean deletableByMe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime resolvedAt
    ) {
    }

    public record CommunityPostSummaryDto(
            Long id,
            Long groupId,
            String groupName,
            String groupColor,
            String groupIcon,
            String type,
            String status,
            String title,
            String contentPreview,
            Boolean pinned,
            CommunityUserDto author,
            List<CommunityProductDto> products,
            List<CommunityAttachmentDto> attachments,
            Integer commentCount,
            List<CommunityReactionSummaryDto> reactions,
            String myReaction,
            int reactionCount,
            CommunityReadStatsDto read,
            CommunityPollDto poll,
            Boolean editableByMe,
            Boolean deletableByMe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime resolvedAt
    ) {
    }

    public record CommunityPostFilters(
            String query,
            Long groupId,
            String status,
            String type,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean pinned,
            Boolean resolved,
            Integer limit,
            Integer offset
    ) {
    }

    public record CreateGroupRequest(
            String name,
            String description,
            String color,
            String icon,
            Boolean active
    ) {
    }

    public record PollInput(
            String question,
            Boolean multiple,
            List<String> options
    ) {
    }

    public record CreatePostRequest(
            Long groupId,
            String type,
            String title,
            String content,
            Boolean pinned,
            List<Long> productIds,
            PollInput poll
    ) {
    }

    public record UpdatePostRequest(
            Long groupId,
            String type,
            String title,
            String content,
            Boolean pinned,
            List<Long> productIds
    ) {
    }

    public record CreateCommentRequest(String content) {
    }

    public record UpdateCommentRequest(String content) {
    }

    public record UpdateStatusRequest(String status, String resolutionComment) {
    }

    public record UpdatePinnedRequest(Boolean pinned) {
    }

    public record ReactionRequest(String reaction) {
    }

    public record PollVoteRequest(List<Long> optionIds) {
    }

    public record CommunityNotificationDto(
            Long id,
            String type,
            String title,
            String message,
            Long postId,
            Long commentId,
            Boolean read,
            LocalDateTime createdAt,
            LocalDateTime readAt
    ) {
    }
}
