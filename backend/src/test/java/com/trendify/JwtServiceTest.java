package com.trendify;

import static org.junit.jupiter.api.Assertions.*;
import com.trendify.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static final String SECRET="0123456789abcdef0123456789abcdef";
  @Test void roundTripPreservesSubjectAndRole(){var service=new JwtService(SECRET,60);var claims=service.parse(service.issue("admin@trendify.dev","ADMIN"));assertEquals("admin@trendify.dev",claims.getSubject());assertEquals("ADMIN",claims.get("role"));assertTrue(claims.getExpiration().after(claims.getIssuedAt()));}
  @Test void tokenFromAnotherKeyIsRejected(){var token=new JwtService(SECRET,60).issue("user","ADMIN");assertThrows(JwtException.class,()->new JwtService("abcdef0123456789abcdef0123456789",60).parse(token));}
  @Test void expiredTokenIsRejected(){var service=new JwtService(SECRET,-1);assertThrows(JwtException.class,()->service.parse(service.issue("user","ADMIN")));}
  @Test void shortSecretIsRejected(){assertThrows(IllegalArgumentException.class,()->new JwtService("too-short",60));}
  @Test void storeScopeRoundTrips(){var service=new JwtService(SECRET,60);assertEquals("SEA-01",service.parse(service.issue("manager","STORE_MANAGER","SEA-01")).get("storeId",String.class));}
}
