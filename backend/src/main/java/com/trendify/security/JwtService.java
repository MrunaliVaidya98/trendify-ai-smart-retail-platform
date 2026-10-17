package com.trendify.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key; private final Duration ttl;
  public JwtService(@Value("${trendify.jwt.secret}") String secret, @Value("${trendify.jwt.expiration-minutes}") long minutes) {
    if (secret.length() < 32) throw new IllegalArgumentException("JWT_SECRET must be at least 32 characters");
    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); ttl = Duration.ofMinutes(minutes);
  }
  public String issue(String subject, String role) {
    return issue(subject,role,"*");
  }
  public String issue(String subject, String role, String storeId) {
    Instant now=Instant.now(); return Jwts.builder().subject(subject).claim("role",role).claim("storeId",storeId).issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl))).signWith(key).compact();
  }
  public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
