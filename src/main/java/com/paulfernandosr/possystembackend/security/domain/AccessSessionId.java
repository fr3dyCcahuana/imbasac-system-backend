package com.paulfernandosr.possystembackend.security.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Public session reference; never expose the legacy refresh credential as a JWT claim. */
public final class AccessSessionId {
    private AccessSessionId() {}

    public static String fromRefreshCredential(UUID refreshCredential) {
        try {
            byte[] input = ("IMBASAC-SESSION:v2:" + refreshCredential).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
