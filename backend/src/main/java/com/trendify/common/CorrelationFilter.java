package com.trendify.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Correlation-ID";
  public static final String REQUEST_ATTRIBUTE = CorrelationFilter.class.getName() + ".id";

  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String id = request.getHeader(HEADER);
    if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
    request.setAttribute(REQUEST_ATTRIBUTE, id);
    try { MDC.put("correlationId", id); response.setHeader(HEADER, id); chain.doFilter(request, response); }
    finally { MDC.remove("correlationId"); }
  }
}
