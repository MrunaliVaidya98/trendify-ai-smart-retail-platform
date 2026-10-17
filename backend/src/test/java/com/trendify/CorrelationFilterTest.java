package com.trendify;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trendify.common.CorrelationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class CorrelationFilterTest {
  @Test void suppliedCorrelationIdIsPropagated() throws Exception {var request=new MockHttpServletRequest();request.addHeader("X-Correlation-ID","test-correlation");var response=new MockHttpServletResponse();new CorrelationFilter().doFilter(request,response,mock(FilterChain.class));assertEquals("test-correlation",response.getHeader("X-Correlation-ID"));assertEquals("test-correlation",request.getAttribute(CorrelationFilter.REQUEST_ATTRIBUTE));}
  @Test void missingCorrelationIdIsGenerated() throws Exception {var response=new MockHttpServletResponse();new CorrelationFilter().doFilter(new MockHttpServletRequest(),response,mock(FilterChain.class));assertNotNull(response.getHeader("X-Correlation-ID"));assertFalse(response.getHeader("X-Correlation-ID").isBlank());}
}
