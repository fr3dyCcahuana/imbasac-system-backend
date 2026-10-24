package com.paulfernandosr.possystembackend.security.infrastructure;

import com.paulfernandosr.possystembackend.security.domain.Token;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JsonWebTokenUtils {
    @Value("${security.token.secret}")
    private String tokenSecret;
    @Value("${security.token.expiration}")
    private long tokenExpiration;
    @Value("${security.token.issuer:urn:imbasac:auth}")
    private String tokenIssuer;
    @Value("${security.token.audience:urn:imbasac:api}")
    private String tokenAudience;

    public Token generateToken(String username, UUID sessionId) {
        String jsonWebToken = Jwts.builder()
                .header().type("at+jwt").and()
                .issuer(tokenIssuer)
                .audience().add(tokenAudience).and()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("sid", AccessSessionId.fromRefreshCredential(sessionId))
                .claim("token_use", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * tokenExpiration))
                .signWith(getSignKey()).compact();
        return new Token(jsonWebToken, "Bearer", tokenExpiration);
    }

    public AccessTokenIdentity parseAccessToken(String token) {
        var jwt = Jwts.parser().verifyWith(getSignKey())
                .requireIssuer(tokenIssuer).require("token_use", "access")
                .build().parseSignedClaims(token);
        Claims claims = jwt.getPayload();
        if (!"at+jwt".equals(jwt.getHeader().getType())
                || claims.getAudience() == null || !claims.getAudience().contains(tokenAudience)
                || claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getId() == null || claims.getId().isBlank()
                || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Invalid access token context");
        }
        String sid = claims.get("sid", String.class);
        if (sid == null || !sid.matches("[a-f0-9]{64}")) {
            throw new JwtException("Invalid access token session");
        }
        return new AccessTokenIdentity(claims.getSubject(), sid);
    }
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(tokenSecret));
    }
    public record AccessTokenIdentity(String username, String sessionId) {}
}
