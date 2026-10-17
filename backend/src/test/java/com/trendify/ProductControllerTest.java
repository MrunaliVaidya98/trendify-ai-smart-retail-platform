package com.trendify.product;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.trendify.common.ApiExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {
  private MockMvc mvc; private ProductStore store;
  @BeforeEach void setup(){store=mock(ProductStore.class);mvc=MockMvcBuilders.standaloneSetup(new ProductController(store)).setControllerAdvice(new ApiExceptionHandler()).build();}
  @Test void invalidProductReturns400() throws Exception {mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"\",\"name\":\"\",\"category\":\"\",\"supplier\":\"\",\"price\":0,\"cost\":-1,\"stockLevel\":-1,\"reorderThreshold\":-1,\"leadTimeDays\":0}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.sku").exists()).andExpect(jsonPath("$.fields.price").exists());verifyNoInteractions(store);}
  @Test void listLimitIsValidated() throws Exception {mvc.perform(get("/api/products").param("limit","101")).andExpect(status().isBadRequest());}
  @Test void missingProductReturns404() throws Exception {when(store.get("SEA-01","missing")).thenThrow(new java.util.NoSuchElementException("Product not found"));mvc.perform(get("/api/products/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Product not found"));}
}
