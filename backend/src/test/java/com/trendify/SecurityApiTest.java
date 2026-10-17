package com.trendify;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@SpringBootTest(properties={"trendify.jwt.secret=0123456789abcdef0123456789abcdef","trendify.jwt.expiration-minutes=60"})
@AutoConfigureMockMvc
class SecurityApiTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper json;
  @MockitoBean DynamoDbClient dynamo;
  private String login(String email) throws Exception {String body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"Trendify123!\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("accessToken").asText();}
  @Test void validAdminLoginReturnsJwtWithoutPassword() throws Exception {mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@trendify.dev\",\"password\":\"Trendify123!\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.accessToken",not(emptyString()))).andExpect(jsonPath("$.password").doesNotExist());}
  @Test void invalidCredentialsReturn401WithoutLeakingDetails() throws Exception {mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@trendify.dev\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized()).andExpect(content().string(not(containsString("Trendify123"))));}
  @Test void missingAndInvalidTokensReturn401() throws Exception {mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isUnauthorized());mvc.perform(get("/api/analytics/dashboard").header("Authorization","Bearer invalid.token.value")).andExpect(status().isUnauthorized());}
  @Test void allRolesCanReadDashboard() throws Exception {for(String email:new String[]{"admin@trendify.dev","manager@trendify.dev","analyst@trendify.dev"})mvc.perform(get("/api/analytics/dashboard").header("Authorization","Bearer "+login(email))).andExpect(status().isOk());}
  @Test void roleMatrixIsEnforced() throws Exception {String product="{\"sku\":\"SKU-TEST\",\"name\":\"Test\",\"category\":\"Test\",\"supplier\":\"Supplier\",\"price\":10,\"cost\":4,\"stockLevel\":3,\"reorderThreshold\":1,\"leadTimeDays\":2}";mvc.perform(post("/api/products").header("Authorization","Bearer "+login("analyst@trendify.dev")).contentType(MediaType.APPLICATION_JSON).content(product)).andExpect(status().isForbidden());mvc.perform(delete("/api/products/id").header("Authorization","Bearer "+login("manager@trendify.dev"))).andExpect(status().isForbidden());mvc.perform(post("/api/products").header("Authorization","Bearer "+login("manager@trendify.dev")).contentType(MediaType.APPLICATION_JSON).content(product)).andExpect(status().isCreated());}
  @Test void corsIsRestricted() throws Exception {mvc.perform(options("/api/products").header("Origin","http://localhost:4200").header("Access-Control-Request-Method","GET")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"));mvc.perform(options("/api/products").header("Origin","https://evil.example").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());}
  @Test void openApiContainsImplementedPaths() throws Exception {String body=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode paths=json.readTree(body).get("paths");assertTrue(paths.has("/api/auth/login"));assertTrue(paths.has("/api/products"));assertTrue(paths.has("/api/analytics/dashboard"));assertTrue(paths.has("/api/orders"));}
  @Test void managerCannotSelectAnotherStore() throws Exception {mvc.perform(get("/api/products").param("storeId","PDX-01").header("Authorization","Bearer "+login("manager@trendify.dev"))).andExpect(status().isForbidden());}
}
