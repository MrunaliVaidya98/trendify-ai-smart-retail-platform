package com.trendify.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
  @Bean PasswordEncoder encoder(){ return new BCryptPasswordEncoder(12); }
  @Bean CorsConfigurationSource cors(@Value("${trendify.cors-origins}") String configuredOrigins){
    List<String> origins=Arrays.stream(configuredOrigins.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();
    var c=new CorsConfiguration(); c.setAllowedOrigins(origins); c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID")); c.setExposedHeaders(List.of("X-Correlation-ID"));
    var s=new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**",c); return s;
  }
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtFilter filter, CorsConfigurationSource cors) throws Exception { return http.csrf(c->c.disable()).cors(c->c.configurationSource(cors)).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint((request,response,exception)->response.sendError(HttpServletResponse.SC_UNAUTHORIZED))).authorizeHttpRequests(a->a
      .requestMatchers("/api/auth/login","/actuator/health/**","/v3/api-docs/**","/swagger-ui/**").permitAll()
      .requestMatchers(HttpMethod.GET,"/api/**").hasAnyRole("ADMIN","STORE_MANAGER","INVENTORY_ANALYST")
      .anyRequest().authenticated()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build(); }
}
