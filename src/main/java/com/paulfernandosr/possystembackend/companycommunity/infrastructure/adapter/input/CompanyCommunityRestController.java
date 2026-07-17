package com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.companycommunity.application.CompanyCommunityService;
import com.paulfernandosr.possystembackend.companycommunity.infrastructure.adapter.input.dto.CompanyCommunityDtos.*;
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
@RequestMapping("/company-community")
public class CompanyCommunityRestController {

    private final CompanyCommunityService companyCommunityService;
    private final ObjectMapper objectMapper;

    @GetMapping("/categories")
    public ResponseEntity<SuccessResponse<List<CompanyCommunityCategoryDto>>> findCategories() {
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.findCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<SuccessResponse<CompanyCommunityCategoryDto>> createCategory(
            @RequestBody CategoryRequest request
    ) {
        CompanyCommunityCategoryDto created = companyCommunityService.createCategory(request);
        return ResponseEntity
                .created(URI.create("/company-community/categories/" + created.id()))
                .body(SuccessResponse.created(created));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<SuccessResponse<CompanyCommunityCategoryDto>> updateCategory(
            @PathVariable Long id,
            @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.updateCategory(id, request)));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        companyCommunityService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    public ResponseEntity<SuccessResponse<List<CompanyCommunityPostSummaryDto>>> findPosts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            Authentication authentication
    ) {
        CompanyCommunityPostFilters filters = new CompanyCommunityPostFilters(
                query,
                categoryId,
                type,
                status,
                pinned,
                dateFrom,
                dateTo,
                limit,
                offset
        );
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.findPosts(filters, authentication)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<SuccessResponse<CompanyCommunityPostDto>> findPost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.findPostById(id, authentication)));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<CompanyCommunityPostDto>> createPost(
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) throws Exception {
        PostRequest request = objectMapper.readValue(data, PostRequest.class);
        CompanyCommunityPostDto created = companyCommunityService.createPost(request, files, authentication);
        return ResponseEntity
                .created(URI.create("/company-community/posts/" + created.id()))
                .body(SuccessResponse.created(created));
    }

    @PutMapping(value = "/posts/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<CompanyCommunityPostDto>> updatePost(
            @PathVariable Long id,
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) throws Exception {
        PostRequest request = objectMapper.readValue(data, PostRequest.class);
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.updatePost(id, request, files, authentication)));
    }

    @PatchMapping("/posts/{id}/status")
    public ResponseEntity<SuccessResponse<CompanyCommunityPostDto>> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.updateStatus(id, request, authentication)));
    }

    @PatchMapping("/posts/{id}/pinned")
    public ResponseEntity<SuccessResponse<CompanyCommunityPostDto>> updatePinned(
            @PathVariable Long id,
            @RequestBody UpdatePinnedRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(companyCommunityService.updatePinned(id, request, authentication)));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        companyCommunityService.deletePost(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        companyCommunityService.deleteAttachment(attachmentId, authentication);
        return ResponseEntity.noContent().build();
    }
}
