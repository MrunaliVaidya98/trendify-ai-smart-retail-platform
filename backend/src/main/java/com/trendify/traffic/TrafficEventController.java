package com.trendify.traffic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendify.security.StoreAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class TrafficEventController {
  public enum EventType { SESSION_STARTED,PAGE_VIEW,PRODUCT_VIEW,SEARCH,ADD_TO_CART,CART_ABANDONED,CHECKOUT_STARTED,PURCHASE_COMPLETED,PURCHASE_FAILED }
  public record TrafficEventRequest(@NotBlank String eventId,@NotBlank String sessionId,@NotNull EventType eventType,String productId,@NotNull Instant occurredAt,Map<String,Object> attributes) {}
  public record IngestionResult(String eventId,boolean created) {}
  public record Metrics(long totalEvents,long activeSessions,long pageViews,long productViews,long checkouts,long purchases,double conversionRate,Instant measuredAt,String storeId) {}
  private final JdbcTemplate jdbc;private final ObjectMapper json;private final StoreAccess access;private final byte[] simulatorKey;
  public TrafficEventController(JdbcTemplate jdbc,ObjectMapper json,StoreAccess access,@Value("${trendify.simulator-key}") String simulatorKey){this.jdbc=jdbc;this.json=json;this.access=access;this.simulatorKey=simulatorKey.getBytes(StandardCharsets.UTF_8);}

  @PostMapping("/events/traffic") @ResponseStatus(HttpStatus.CREATED)
  public IngestionResult ingest(@RequestHeader("X-Simulator-Key") String supplied,@RequestParam(required=false) String storeId,@Valid @RequestBody TrafficEventRequest request,Authentication authentication){
    if(!MessageDigest.isEqual(simulatorKey,supplied.getBytes(StandardCharsets.UTF_8)))throw new org.springframework.security.access.AccessDeniedException("Invalid simulator credential");
    String resolved=access.resolve(authentication,storeId);String attributes=toJson(request.attributes()==null?Map.of():request.attributes());
    try{jdbc.update("INSERT INTO traffic_events (id,store_id,session_id,event_type,product_id,occurred_at,attributes_json) VALUES (?,?,?,?,?,?,?)",request.eventId(),resolved,request.sessionId(),request.eventType().name(),request.productId(),Timestamp.from(request.occurredAt()),attributes);return new IngestionResult(request.eventId(),true);}catch(DuplicateKeyException duplicate){return new IngestionResult(request.eventId(),false);}
  }

  @GetMapping("/traffic/metrics")
  public Metrics metrics(@RequestParam(defaultValue="15") int minutes,@RequestParam(required=false) String storeId,Authentication authentication){
    if(minutes<1||minutes>1440)throw new IllegalArgumentException("minutes must be between 1 and 1440");String resolved=access.resolve(authentication,storeId);Instant since=Instant.now().minusSeconds(minutes*60L);Map<String,Object> row=jdbc.queryForMap("SELECT COUNT(*) AS \"total_events\", COUNT(DISTINCT session_id) AS \"active_sessions\", COALESCE(SUM(CASE WHEN event_type='PAGE_VIEW' THEN 1 ELSE 0 END),0) AS \"page_views\", COALESCE(SUM(CASE WHEN event_type='PRODUCT_VIEW' THEN 1 ELSE 0 END),0) AS \"product_views\", COALESCE(SUM(CASE WHEN event_type='CHECKOUT_STARTED' THEN 1 ELSE 0 END),0) AS \"checkouts\", COALESCE(SUM(CASE WHEN event_type='PURCHASE_COMPLETED' THEN 1 ELSE 0 END),0) AS \"purchases\" FROM traffic_events WHERE store_id=? AND occurred_at>=?",resolved,Timestamp.from(since));long sessions=number(row,"active_sessions"),purchases=number(row,"purchases");return new Metrics(number(row,"total_events"),sessions,number(row,"page_views"),number(row,"product_views"),number(row,"checkouts"),purchases,sessions==0?0:(double)purchases/sessions,Instant.now(),resolved);
  }
  private long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
  private String toJson(Map<String,Object> value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Event attributes are not valid JSON");}}
}
