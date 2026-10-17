package com.trendify.auth;

import com.trendify.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  private record User(String hash,String role,String name,String storeId) {}
  public record LoginRequest(@NotBlank String email,@NotBlank String password) {}
  public record LoginResponse(String accessToken,String tokenType,long expiresIn,String role,String displayName,String storeId) {}
  private final JwtService jwt; private final PasswordEncoder encoder; private final Map<String,User> users;
  public AuthController(JwtService jwt,PasswordEncoder encoder){ this.jwt=jwt; this.encoder=encoder; users=Map.of(
      "admin@trendify.dev",new User(encoder.encode("Trendify123!"),"ADMIN","Avery Admin","*"),
      "manager@trendify.dev",new User(encoder.encode("Trendify123!"),"STORE_MANAGER","Morgan Manager","SEA-01"),
      "portland@trendify.dev",new User(encoder.encode("Trendify123!"),"STORE_MANAGER","PDX-01 Manager","PDX-01"),
      "analyst@trendify.dev",new User(encoder.encode("Trendify123!"),"INVENTORY_ANALYST","Indy Analyst","SEA-01")); }
  @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest r){
    User u=users.get(r.email().toLowerCase()); if(u==null || !encoder.matches(r.password(),u.hash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
    return new LoginResponse(jwt.issue(r.email().toLowerCase(),u.role(),u.storeId()),"Bearer",3600,u.role(),u.name(),u.storeId());
  }
}
