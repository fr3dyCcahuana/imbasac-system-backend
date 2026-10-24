package com.paulfernandosr.possystembackend.security.infrastructure;

import io.jsonwebtoken.JwtException;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JsonWebTokenUtilsTest {
    private final SecretKey key = Jwts.SIG.HS256.key().build();
    private final JsonWebTokenUtils tokens = new JsonWebTokenUtils();
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(tokens, "tokenSecret", Base64.getEncoder().encodeToString(key.getEncoded()));
        ReflectionTestUtils.setField(tokens, "tokenExpiration", 300L);
        ReflectionTestUtils.setField(tokens, "tokenIssuer", "urn:imbasac:auth");
        ReflectionTestUtils.setField(tokens, "tokenAudience", "urn:imbasac:api");
    }
    @Test void bindsTokenToExactSessionAndPreservesUsername() {
        UUID session = UUID.randomUUID();
        var first = tokens.generateToken("client", session);
        var second = tokens.generateToken("client", session);
        assertThat(tokens.parseAccessToken(first.value()))
                .isEqualTo(new JsonWebTokenUtils.AccessTokenIdentity("client", AccessSessionId.fromRefreshCredential(session)));
        assertThat(first.value()).isNotEqualTo(second.value());
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(first.value()).getPayload();
        assertThat(claims.get("sid", String.class)).doesNotContain(session.toString());
        assertThat(first.expiresIn()).isEqualTo(300);
    }
    @Test void rejectsLegacyTokenWithoutSessionContext() {
        String old = Jwts.builder().subject("client").expiration(new Date(System.currentTimeMillis()+300000))
                .signWith(key).compact();
        assertThatThrownBy(() -> tokens.parseAccessToken(old)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsWrongIssuerAudienceTypePurposeAndSession() {
        for (JwtBuilder invalid : new JwtBuilder[]{
                valid().issuer("other"), valid().audience().clear().add("other").and(),
                valid().header().type("JWT").and(), valid().claim("token_use", "refresh"),
                valid().claim("sid", "broken"), valid().claim("sid", null), valid().expiration(null),
                valid().subject(" "), valid().id(null)}) {
            String value = invalid.signWith(key).compact();
            assertThatThrownBy(() -> tokens.parseAccessToken(value)).isInstanceOf(JwtException.class);
        }
    }
    @Test void rejectsExpiredAndDifferentSignature() {
        String expired = valid().expiration(new Date(System.currentTimeMillis()-10000)).signWith(key).compact();
        String wrongKey = valid().signWith(Jwts.SIG.HS256.key().build()).compact();
        assertThatThrownBy(() -> tokens.parseAccessToken(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.parseAccessToken(wrongKey)).isInstanceOf(JwtException.class);
    }
    private JwtBuilder valid() {
        return Jwts.builder().header().type("at+jwt").and().issuer("urn:imbasac:auth")
                .audience().add("urn:imbasac:api").and().subject("client").id(UUID.randomUUID().toString())
                .claim("sid", AccessSessionId.fromRefreshCredential(UUID.randomUUID())).claim("token_use", "access")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+300000));
    }
}
