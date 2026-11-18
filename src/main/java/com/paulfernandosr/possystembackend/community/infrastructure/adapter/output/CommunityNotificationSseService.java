package com.paulfernandosr.possystembackend.community.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.community.infrastructure.adapter.input.dto.CommunityDtos.CommunityNotificationDto;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mantiene los emisores SSE conectados por usuario para enviar notificaciones
 * de Comunidad interna en tiempo real (push), sin esperar al polling.
 */
@Service
public class CommunityNotificationSseService {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L; // 30 min

    private final Map<Long, List<Subscription>> emitters = new ConcurrentHashMap<>();
    private final SessionRepository sessions;
    private final ControlSessionGuard control;
    private final UserDetailsService users;
    public CommunityNotificationSseService(SessionRepository sessions,ControlSessionGuard control,UserDetailsService users) {
        this.sessions=sessions;this.control=control;this.users=users;
    }
    private record Subscription(SseEmitter emitter,String username,String sid,Instant expires) {}

    public SseEmitter subscribe(Long userId,String username,String sid,Instant expires) {
        Instant cutoff=Instant.now().plusMillis(SSE_TIMEOUT_MS);
        if(expires.isBefore(cutoff)) cutoff=expires;
        SseEmitter emitter = new SseEmitter(Math.max(1,Duration.between(Instant.now(),cutoff).toMillis()));
        Subscription subscription=new Subscription(emitter,username,sid,cutoff);
        if(!valid(subscription)) throw new org.springframework.security.access.AccessDeniedException("Session not active");
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(subscription);

        emitter.onCompletion(() -> remove(userId, subscription));
        emitter.onTimeout(() -> remove(userId, subscription));
        emitter.onError(throwable -> remove(userId, subscription));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException exception) {
            close(userId, subscription);
        }
        return emitter;
    }

    public void push(Long userId, CommunityNotificationDto notification) {
        List<Subscription> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        for (Subscription subscription : userEmitters) {
            if(!valid(subscription)) { close(userId,subscription);continue; }
            try {
                subscription.emitter().send(SseEmitter.event().name("notification").data(notification));
            } catch (Exception exception) {
                close(userId, subscription);
            }
        }
    }

    @Scheduled(fixedDelay=15000)
    public void revalidate() {
        emitters.forEach((id,subscriptions)->subscriptions.forEach(subscription->{
            if(!valid(subscription)) close(id,subscription);
            else try { subscription.emitter().send(SseEmitter.event().comment("keepalive")); }
            catch(Exception ignored) { close(id,subscription); }
        }));
    }
    private boolean valid(Subscription subscription) {
        try {
            var user=users.loadUserByUsername(subscription.username());
            return subscription.expires().isAfter(Instant.now()) && user.isEnabled() && user.isAccountNonLocked()
                    && user.isAccountNonExpired() && user.isCredentialsNonExpired()
                    && sessions.isActive(subscription.sid(),subscription.username())
                    && control.active(subscription.sid(),subscription.username(),false);
        } catch(Exception ignored) { return false; }
    }
    private void close(Long id,Subscription subscription) { remove(id,subscription);try { subscription.emitter().complete(); } catch(Exception ignored) {} }
    private void remove(Long id,Subscription subscription) {
        emitters.computeIfPresent(id,(key,list)->{list.remove(subscription);return list.isEmpty()?null:list;});
    }
}
