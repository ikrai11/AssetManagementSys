package com.ikrai.project.config;

import com.ikrai.project.common.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties properties;

    public String createToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getExpireHours(), ChronoUnit.HOURS)))
                .signWith(key())
                .compact();
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                Long.parseLong(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class),
                claims.get("tokenVersion", Integer.class)
        );
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
