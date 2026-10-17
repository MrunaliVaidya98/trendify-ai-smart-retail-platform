package com.trendify.recommendation;

import com.trendify.recommendation.RecommendationService.*;
import com.trendify.security.StoreAccess;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/recommendations")
public class RecommendationController {
  public record SavedRecommendation(String id,String storeId,String productId,PricingResult scenario,String status,String decidedBy,Instant decidedAt) {}
  public record AuditEntry(String status,String actor,Instant decidedAt){}
  public interface Repository {SavedRecommendation save(SavedRecommendation value);SavedRecommendation get(String storeId,String id);SavedRecommendation decide(String storeId,String id,boolean approve,String actor);List<AuditEntry> history(String storeId,String id);}
  private final RecommendationService service;private final Repository records;private final StoreAccess access;
  @org.springframework.beans.factory.annotation.Autowired public RecommendationController(RecommendationService service,PriceRecommendationStore records,StoreAccess access){this(service,(Repository)records,access);}
  RecommendationController(RecommendationService service,Repository records,StoreAccess access){this.service=service;this.records=records;this.access=access;}
  public RecommendationController(RecommendationService service){this(service,new MemoryRepository(),new StoreAccess());}
  @PostMapping("/pricing/{productId}") public SavedRecommendation price(@PathVariable String productId,@Valid @RequestBody PricingInput input,@RequestParam(required=false) String storeId,Authentication auth){String resolved=access.resolve(auth,storeId);String id=UUID.randomUUID().toString();return records.save(new SavedRecommendation(id,resolved,productId,service.price(input),"PENDING",null,null));}
  public SavedRecommendation price(String productId,PricingInput input){return price(productId,input,null,null);}
  @PostMapping("/reorder") public ReorderResult reorder(@RequestBody ReorderInput input){return service.reorder(input);}
  @PostMapping("/pricing/{id}/decision") @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')") public SavedRecommendation decide(@PathVariable String id,@RequestParam boolean approve,@RequestParam(required=false) String storeId,Authentication principal){return records.decide(access.resolve(principal,storeId),id,approve,principal.getName());}
  public SavedRecommendation decide(String id,boolean approve,java.security.Principal principal){return records.decide("SEA-01",id,approve,principal.getName());}
  @GetMapping("/pricing/{id}") public SavedRecommendation get(@PathVariable String id,@RequestParam(required=false) String storeId,Authentication auth){return records.get(access.resolve(auth,storeId),id);}
  @GetMapping("/pricing/{id}/history") public List<AuditEntry> history(@PathVariable String id,@RequestParam(required=false) String storeId,Authentication auth){return records.history(access.resolve(auth,storeId),id);}
  static class MemoryRepository implements Repository {private final Map<String,SavedRecommendation> values=new ConcurrentHashMap<>();private final Map<String,List<AuditEntry>> audits=new ConcurrentHashMap<>();public SavedRecommendation save(SavedRecommendation v){values.put(v.id(),v);return v;}public SavedRecommendation get(String store,String id){var v=values.get(id);if(v==null||!store.equals(v.storeId()))throw new NoSuchElementException("Recommendation not found");return v;}public SavedRecommendation decide(String store,String id,boolean approve,String actor){var old=get(store,id);if(!"PENDING".equals(old.status()))throw new IllegalArgumentException("Recommendation was already decided");var at=Instant.now();var next=new SavedRecommendation(old.id(),store,old.productId(),old.scenario(),approve?"APPROVED":"REJECTED",actor,at);values.put(id,next);audits.computeIfAbsent(id,k->new ArrayList<>()).add(new AuditEntry(next.status(),actor,at));return next;}public List<AuditEntry> history(String store,String id){get(store,id);return List.copyOf(audits.getOrDefault(id,List.of()));}}
}
