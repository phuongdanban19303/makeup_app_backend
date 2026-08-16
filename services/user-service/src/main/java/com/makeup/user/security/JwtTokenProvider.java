package com.makeup.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final long jwtExpirationInMs;
    private final long refreshTokenExpirationInMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:MakeupAppSecretKeySuperSecretForJWTTokenGeneration2026!}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long jwtExpirationInMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshTokenExpirationInMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.jwtExpirationInMs = jwtExpirationInMs;
        this.refreshTokenExpirationInMs = refreshTokenExpirationInMs;
    }

    public long getRefreshTokenExpirationInMs() {
        return refreshTokenExpirationInMs;
    }

    public String generateToken(Long userId, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("roles", roles)
                .claim("type", "ACCESS")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationInMs);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", "REFRESH")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    public List<String> getRolesFromToken(String token) {
        Claims claims = getClaims(token);
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream().map(Object::toString).toList();
    }

    public String getTokenType(String token) {
        Claims claims = getClaims(token);
        return claims.get("type", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

