package com.paulfernandosr.possystembackend.community.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.community.application.CommunityService;
import com.paulfernandosr.possystembackend.community.infrastructure.adapter.input.dto.CommunityDtos.CommunityNotificationDto;
import com.paulfernandosr.possystembackend.community.infrastructure.adapter.output.CommunityNotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/community-notifications")
public class CommunityNotificationRestController {

    private final CommunityService communityService;
    private final CommunityNotificationSseService sseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication,jakarta.servlet.http.HttpServletRequest request) {
        Long userId = communityService.currentUserId(authentication);
        if(!(request.getAttribute("imbasac.accessSessionKey") instanceof String sid)
                || !(request.getAttribute("imbasac.accessTokenExpiresAt") instanceof java.time.Instant expires))
            throw new org.springframework.security.access.AccessDeniedException("Session required");
        return sseService.subscribe(userId,authentication.getName(),sid,expires);
    }

    @GetMapping("/unread")
    public ResponseEntity<SuccessResponse<List<CommunityNotificationDto>>> unread(
            Authentication authentication
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(communityService.findUnreadNotifications(authentication)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        communityService.markNotificationRead(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        communityService.markAllNotificationsRead(authentication);
        return ResponseEntity.noContent().build();
    }
}
