package com.trendify.recommendation;

import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder;
import com.trendify.product.ProductStore;
import com.trendify.recommendation.RecommendationController.*;
import com.trendify.recommendation.RecommendationService.PricingResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
public class PriceRecommendationStore implements RecommendationController.Repository {
  private final DynamoDbClient db;private final String table;
  public PriceRecommendationStore(DynamoDbClient db,ProductStore products){this.db=db;this.table=products.table();}
  public SavedRecommendation save(SavedRecommendation value){db.putItem(r->r.tableName(table).item(item(value)).conditionExpression("attribute_not_exists(PK)"));return value;}
  public SavedRecommendation get(String storeId,String id){var out=db.getItem(r->r.tableName(table).key(key(id)).consistentRead(true));if(!out.hasItem()||!storeId.equals(out.item().get("storeId").s()))throw new NoSuchElementException("Recommendation not found");return from(out.item());}
  public SavedRecommendation decide(String storeId,String id,boolean approve,String actor){SavedRecommendation old=get(storeId,id);if(!"PENDING".equals(old.status()))throw new IllegalArgumentException("Recommendation was already decided");Instant at=Instant.now();SavedRecommendation next=new SavedRecommendation(old.id(),old.storeId(),old.productId(),old.scenario(),approve?"APPROVED":"REJECTED",actor,at);
    Map<String,AttributeValue> audit=new HashMap<>();audit.put("PK",builder().s("PRICE_REC#"+id).build());audit.put("SK",builder().s("AUDIT#"+at).build());audit.put("entityType",builder().s("PRICE_AUDIT").build());audit.put("storeId",builder().s(storeId).build());audit.put("status",builder().s(next.status()).build());audit.put("actor",builder().s(actor).build());
    try{db.transactWriteItems(r->r.transactItems(TransactWriteItem.builder().put(Put.builder().tableName(table).item(item(next)).conditionExpression("#status = :pending AND storeId = :store").expressionAttributeNames(Map.of("#status","status")).expressionAttributeValues(Map.of(":pending",builder().s("PENDING").build(),":store",builder().s(storeId).build())).build()).build(),TransactWriteItem.builder().put(Put.builder().tableName(table).item(audit).conditionExpression("attribute_not_exists(SK)").build()).build()));}catch(TransactionCanceledException e){throw new IllegalArgumentException("Recommendation was already decided");}return next;}
  public List<AuditEntry> history(String storeId,String id){get(storeId,id);var out=db.query(r->r.tableName(table).keyConditionExpression("PK = :pk AND begins_with(SK,:prefix)").expressionAttributeValues(Map.of(":pk",builder().s("PRICE_REC#"+id).build(),":prefix",builder().s("AUDIT#").build())).scanIndexForward(true));return out.items().stream().map(i->new AuditEntry(i.get("status").s(),i.get("actor").s(),Instant.parse(i.get("SK").s().substring(6)))).toList();}
  private Map<String,AttributeValue> key(String id){return Map.of("PK",builder().s("PRICE_REC#"+id).build(),"SK",builder().s("META").build());}
  private Map<String,AttributeValue> item(SavedRecommendation v){Map<String,AttributeValue> m=new HashMap<>(key(v.id()));m.put("entityType",builder().s("PRICE_RECOMMENDATION").build());m.put("id",builder().s(v.id()).build());m.put("storeId",builder().s(v.storeId()).build());m.put("productId",builder().s(v.productId()).build());m.put("status",builder().s(v.status()).build());m.put("originalPrice",builder().n(v.scenario().originalPrice().toPlainString()).build());m.put("suggestedPrice",builder().n(v.scenario().suggestedPrice().toPlainString()).build());m.put("estimatedDailyRevenue",builder().n(v.scenario().estimatedDailyRevenue().toPlainString()).build());m.put("reason",builder().s(v.scenario().reason()).build());if(v.decidedBy()!=null)m.put("decidedBy",builder().s(v.decidedBy()).build());if(v.decidedAt()!=null)m.put("decidedAt",builder().s(v.decidedAt().toString()).build());return m;}
  private SavedRecommendation from(Map<String,AttributeValue> m){PricingResult result=new PricingResult(new BigDecimal(m.get("originalPrice").n()),new BigDecimal(m.get("suggestedPrice").n()),new BigDecimal(m.get("estimatedDailyRevenue").n()),m.get("reason").s());return new SavedRecommendation(m.get("id").s(),m.get("storeId").s(),m.get("productId").s(),result,m.get("status").s(),m.containsKey("decidedBy")?m.get("decidedBy").s():null,m.containsKey("decidedAt")?Instant.parse(m.get("decidedAt").s()):null);}
}
