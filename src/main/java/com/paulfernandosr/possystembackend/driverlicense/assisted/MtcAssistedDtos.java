package com.paulfernandosr.possystembackend.driverlicense.assisted;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class MtcAssistedDtos {
    private MtcAssistedDtos() {}
    public record StartRequest(@NotBlank String documentType, @NotBlank String documentNumber) {}
    public record AnswerRequest(@NotBlank String challengeId, @NotNull @Min(0) Integer imageIndex,
                                @AssertTrue boolean privacyAccepted) {}
    public record Option(int index, String image) {}
    public record Challenge(String sessionId, String challengeId, String documentType, String documentNumber,
                            String question, List<Option> options, Instant expiresAt) {}
    public record License(String number, String category, String status, String validUntil,
                          String restrictions, String type, String issuedAt) {}
    public record Result(String documentType, String documentNumber,
                         String names, String surnames, List<License> licenses, Integer accumulatedPoints,
                         boolean complete, String source, Instant queriedAt,
                         List<String> warnings) {}
    public record Status(boolean enabled, String privacyUrl, long sessionTtlSeconds) {}
}
