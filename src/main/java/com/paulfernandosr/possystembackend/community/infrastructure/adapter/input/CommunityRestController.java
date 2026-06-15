package com.paulfernandosr.possystembackend.community.infrastructure.adapter.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.community.application.CommunityService;
import com.paulfernandosr.possystembackend.community.infrastructure.adapter.input.dto.CommunityDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/community")
public class CommunityRestController {

    private final CommunityService communityService;
    private final ObjectMapper objectMapper;

    @GetMapping("/groups")
    public ResponseEntity<SuccessResponse<List<CommunityGroupDto>>> findGroups() {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.findGroups()));
    }

    @PostMapping("/groups")
    public ResponseEntity<SuccessResponse<CommunityGroupDto>> createGroup(
            @RequestBody CreateGroupRequest request
    ) {
        CommunityGroupDto created = communityService.createGroup(request);
        return ResponseEntity
                .created(URI.create("/community/groups/" + created.id()))
                .body(SuccessResponse.created(created));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<SuccessResponse<CommunityGroupDto>> updateGroup(
            @PathVariable Long id,
            @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.updateGroup(id, request)));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        communityService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    public ResponseEntity<SuccessResponse<List<CommunityPostSummaryDto>>> findPosts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            Authentication authentication
    ) {
        CommunityPostFilters filters = new CommunityPostFilters(
                query,
                groupId,
                status,
                type,
                userId,
                dateFrom,
                dateTo,
                pinned,
                resolved,
                limit,
                offset
        );
        return ResponseEntity.ok(SuccessResponse.ok(communityService.findPosts(filters, authentication)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> findPost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.findPostById(id, authentication)));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<CommunityPostDto>> createPost(
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) throws Exception {
        CreatePostRequest request = objectMapper.readValue(data, CreatePostRequest.class);
        CommunityPostDto created = communityService.createPost(request, files, authentication);
        return ResponseEntity
                .created(URI.create("/community/posts/" + created.id()))
                .body(SuccessResponse.created(created));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> updatePost(
            @PathVariable Long id,
            @RequestBody UpdatePostRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.updatePost(id, request, authentication)));
    }

    @PatchMapping("/posts/{id}/status")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.updateStatus(id, request, authentication)));
    }

    @PatchMapping("/posts/{id}/pinned")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> updatePinned(
            @PathVariable Long id,
            @RequestBody UpdatePinnedRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.updatePinned(id, request, authentication)));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        communityService.deletePost(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/posts/{postId}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<CommunityPostDto>> createComment(
            @PathVariable Long postId,
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) throws Exception {
        CreateCommentRequest request = objectMapper.readValue(data, CreateCommentRequest.class);
        return ResponseEntity.ok(SuccessResponse.ok(communityService.createComment(postId, request, files, authentication)));
    }

    @PutMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody UpdateCommentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.updateComment(postId, commentId, request, authentication)));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication authentication
    ) {
        communityService.deleteComment(postId, commentId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> react(
            @PathVariable Long id,
            @RequestBody ReactionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.react(id, request, authentication)));
    }

    @DeleteMapping("/posts/{id}/reactions")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> removeReaction(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.removeReaction(id, authentication)));
    }

    @PostMapping("/posts/{id}/read")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> markRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.markRead(id, authentication)));
    }

    @PostMapping("/posts/{id}/poll/votes")
    public ResponseEntity<SuccessResponse<CommunityPostDto>> votePoll(
            @PathVariable Long id,
            @RequestBody PollVoteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.votePoll(id, request, authentication)));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        communityService.deleteAttachment(attachmentId, authentication);
        return ResponseEntity.noContent().build();
    }
}
