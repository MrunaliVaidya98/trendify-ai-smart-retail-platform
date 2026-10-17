package com.trendify.traffic;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.time.Instant;

@SpringBootTest(properties={"trendify.jwt.secret=0123456789abcdef0123456789abcdef","trendify.simulator-key=test-simulator-key"})
@AutoConfigureMockMvc
class TrafficEventControllerTest {
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@MockitoBean DynamoDbClient dynamo;
  @BeforeEach void clean(){jdbc.update("DELETE FROM traffic_events");}
  private String token() throws Exception {String body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"manager@trendify.dev\",\"password\":\"Trendify123!\"}")).andReturn().getResponse().getContentAsString();return json.readTree(body).get("accessToken").asText();}
  private String event(String id,String type){return "{\"eventId\":\""+id+"\",\"sessionId\":\"session-1\",\"eventType\":\""+type+"\",\"occurredAt\":\""+Instant.now()+"\",\"attributes\":{\"source\":\"test\"}}";}
  @Test void eventIsPersistedAndDuplicateIsIdempotent() throws Exception {String token=token();mvc.perform(post("/api/v1/events/traffic").header("Authorization","Bearer "+token).header("X-Simulator-Key","test-simulator-key").contentType(MediaType.APPLICATION_JSON).content(event("event-1","PAGE_VIEW"))).andExpect(status().isCreated()).andExpect(jsonPath("$.created").value(true));mvc.perform(post("/api/v1/events/traffic").header("Authorization","Bearer "+token).header("X-Simulator-Key","test-simulator-key").contentType(MediaType.APPLICATION_JSON).content(event("event-1","PAGE_VIEW"))).andExpect(status().isCreated()).andExpect(jsonPath("$.created").value(false));}
  @Test void invalidSimulatorKeyIsRejected() throws Exception {mvc.perform(post("/api/v1/events/traffic").header("Authorization","Bearer "+token()).header("X-Simulator-Key","wrong").contentType(MediaType.APPLICATION_JSON).content(event("event-2","PAGE_VIEW"))).andExpect(status().isForbidden());}
  @Test void persistedMetricsAreAggregatedByStore() throws Exception {String token=token();for(String type:new String[]{"SESSION_STARTED","PAGE_VIEW","PRODUCT_VIEW","CHECKOUT_STARTED","PURCHASE_COMPLETED"})mvc.perform(post("/api/v1/events/traffic").header("Authorization","Bearer "+token).header("X-Simulator-Key","test-simulator-key").contentType(MediaType.APPLICATION_JSON).content(event("event-"+type,type))).andExpect(status().isCreated());mvc.perform(get("/api/v1/traffic/metrics").param("minutes","1440").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalEvents").value(5)).andExpect(jsonPath("$.pageViews").value(1)).andExpect(jsonPath("$.purchases").value(1)).andExpect(jsonPath("$.conversionRate").value(1.0));}
}
