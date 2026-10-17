package com.trendify;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.trendify.common.ApiExceptionHandler;
import com.trendify.common.CorrelationFilter;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {
  private MockMvc mvc;

  @BeforeEach void setup() {
    mvc=MockMvcBuilders.standaloneSetup(new ThrowingController())
        .setControllerAdvice(new ApiExceptionHandler())
        .addFilters(new CorrelationFilter())
        .build();
  }

  @Test void notFoundResponseIncludesPathAndCorrelationId() throws Exception {
    mvc.perform(get("/test/missing").header(CorrelationFilter.HEADER,"request-123"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Product not found"))
        .andExpect(jsonPath("$.path").value("/test/missing"))
        .andExpect(jsonPath("$.correlationId").value("request-123"));
  }

  @Test void accessDeniedResponseDoesNotExposeInternalReason() throws Exception {
    mvc.perform(get("/test/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Access is denied"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(result->assertFalse(result.getResponse().getContentAsString().contains("secret tenant")));
  }

  @RestController static class ThrowingController {
    @GetMapping("/test/missing") void missing(){throw new NoSuchElementException("Product not found");}
    @GetMapping("/test/denied") void denied(){throw new AccessDeniedException("secret tenant mismatch");}
  }
}
