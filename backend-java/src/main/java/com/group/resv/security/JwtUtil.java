package com.group.resv.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。密钥与 Python 端共用（HS256），走环境变量 JWT_SECRET。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration ttl;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.ttl-minutes:720}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String generate(AuthUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("username", user.username())
                .claim("name", user.name())
                .claim("department", user.department())
                .claim("position", user.position())
                .claim("role", user.role())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析。无效或过期抛异常。
     */
    public AuthUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new AuthUser(
                Long.valueOf(c.getSubject()),
                c.get("username", String.class),
                c.get("name", String.class),
                c.get("department", String.class),
                c.get("position", String.class),
                c.get("role", String.class));
    }
}
