package com.trendify.product;

import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder;
import com.trendify.product.ProductController.*;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
public class ProductStore {
  private static final String DEFAULT_STORE="SEA-01";
  private final DynamoDbClient db; private final String table;
  public ProductStore(DynamoDbClient db,@Value("${trendify.aws.table-name}") String table){this.db=db;this.table=table;}
  @PostConstruct void initialize(){
    try{db.describeTable(r->r.tableName(table));}catch(ResourceNotFoundException e){db.createTable(r->r.tableName(table).billingMode(BillingMode.PAY_PER_REQUEST).keySchema(KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build()).attributeDefinitions(AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build()));db.waiter().waitUntilTableExists(r->r.tableName(table));seed();}
  }
  private void seed(){
    create(DEFAULT_STORE,new ProductInput("SKU-COFFEE-01","Northstar Cold Brew","Beverages","Northstar Roasters",new BigDecimal("5.99"),new BigDecimal("2.10"),84,30,5));
    create(DEFAULT_STORE,new ProductInput("SKU-TOTE-01","Canvas Market Tote","Accessories","Common Goods",new BigDecimal("18.00"),new BigDecimal("6.25"),19,24,12));
    create(DEFAULT_STORE,new ProductInput("SKU-CANDLE-01","Cedar & Sage Candle","Home","Field & Flame",new BigDecimal("28.00"),new BigDecimal("9.80"),43,15,9));
    String[] categories={"Beverages","Accessories","Home","Grocery","Health","Electronics","Beauty","Outdoors","Office","Pet Care"};
    String[] suppliers={"Northstar Supply","Common Goods","Pacific Wholesale","Evergreen Brands","Summit Distribution"};
    for(int number=1;number<=100;number++){
      String category=categories[(number-1)%categories.length];BigDecimal price=BigDecimal.valueOf(499L+(number%37)*135L,2);BigDecimal cost=price.multiply(new BigDecimal("0.45")).setScale(2,java.math.RoundingMode.HALF_UP);
      create(DEFAULT_STORE,new ProductInput("DEMO-%04d".formatted(number),"Demo %s Product %03d".formatted(category,number),category,suppliers[(number-1)%suppliers.length],price,cost,12+(number*7)%180,8+number%20,2+number%14));
    }
  }
  Product create(ProductInput input){return create(DEFAULT_STORE,input);}
  public Product create(String storeId,ProductInput input){
    String id=UUID.randomUUID().toString();Product product=new Product(id,input.sku(),input.name(),input.category(),input.supplier(),input.price(),input.cost(),input.stockLevel(),input.reorderThreshold(),input.leadTimeDays(),1,storeId);
    try{db.transactWriteItems(r->r.transactItems(
        TransactWriteItem.builder().put(Put.builder().tableName(table).item(item(product)).conditionExpression("attribute_not_exists(PK)").build()).build(),
        TransactWriteItem.builder().put(Put.builder().tableName(table).item(skuLock(storeId,input.sku(),id)).conditionExpression("attribute_not_exists(PK)").build()).build()));
    }catch(TransactionCanceledException e){throw new IllegalArgumentException("SKU already exists in this store");}
    return product;
  }
  Product get(String id){return get(DEFAULT_STORE,id);}
  public Product get(String storeId,String id){var out=db.getItem(r->r.tableName(table).key(key(id)).consistentRead(true));if(!out.hasItem()||!storeId.equals(out.item().get("storeId").s()))throw new NoSuchElementException("Product not found");return from(out.item());}
  Page list(int limit,String token){return list(DEFAULT_STORE,null,limit,token);}
  public Page list(String storeId,String category,int limit,String token){
    Map<String,AttributeValue> start=decodeToken(token);
    Map<String,AttributeValue> values=new HashMap<>();values.put(":type",builder().s("PRODUCT").build());values.put(":store",builder().s(storeId).build());String filter="entityType = :type AND storeId = :store";if(category!=null&&!category.isBlank()){filter+=" AND category = :category";values.put(":category",builder().s(category).build());}
    List<Product> matches=new ArrayList<>();Map<String,AttributeValue> cursor=start;
    do{var req=ScanRequest.builder().tableName(table).filterExpression(filter).expressionAttributeValues(values).limit(Math.max(1,limit-matches.size()));if(cursor!=null)req.exclusiveStartKey(cursor);var out=db.scan(req.build());matches.addAll(out.items().stream().map(this::from).toList());cursor=out.lastEvaluatedKey().isEmpty()?null:out.lastEvaluatedKey();}while(matches.size()<limit&&cursor!=null);
    return new Page(matches,cursor==null?null:encodeToken(cursor));
  }
  private String encodeToken(Map<String,AttributeValue> key){String raw=key.get("PK").s()+"\n"+key.get("SK").s();return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));}
  private Map<String,AttributeValue> decodeToken(String token){if(token==null||token.isBlank())return null;try{String raw=new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8);String[] parts=raw.split("\\n",-1);if(parts.length!=2||parts[0].isBlank()||parts[1].isBlank())throw new IllegalArgumentException();return Map.of("PK",builder().s(parts[0]).build(),"SK",builder().s(parts[1]).build());}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid pagination token");}}
  Product update(String id,long version,ProductInput input){return update(DEFAULT_STORE,id,version,input);}
  public Product update(String storeId,String id,long version,ProductInput input){
    Product old=get(storeId,id);Product next=new Product(id,input.sku(),input.name(),input.category(),input.supplier(),input.price(),input.cost(),input.stockLevel(),input.reorderThreshold(),input.leadTimeDays(),version+1,storeId);
    List<TransactWriteItem> writes=new ArrayList<>();
    writes.add(TransactWriteItem.builder().put(Put.builder().tableName(table).item(item(next)).conditionExpression("#v = :v AND storeId = :store").expressionAttributeNames(Map.of("#v","version")).expressionAttributeValues(Map.of(":v",builder().n(Long.toString(version)).build(),":store",builder().s(storeId).build())).build()).build());
    if(!old.sku().equals(input.sku())){writes.add(TransactWriteItem.builder().put(Put.builder().tableName(table).item(skuLock(storeId,input.sku(),id)).conditionExpression("attribute_not_exists(PK)").build()).build());writes.add(TransactWriteItem.builder().delete(Delete.builder().tableName(table).key(skuKey(storeId,old.sku())).build()).build());}
    try{db.transactWriteItems(r->r.transactItems(writes));}catch(TransactionCanceledException e){throw new IllegalArgumentException("Product was modified or SKU already exists; refresh and retry");}return next;
  }
  void delete(String id){delete(DEFAULT_STORE,id);}
  public void delete(String storeId,String id){Product product=get(storeId,id);db.transactWriteItems(r->r.transactItems(TransactWriteItem.builder().delete(Delete.builder().tableName(table).key(key(id)).conditionExpression("storeId = :store").expressionAttributeValues(Map.of(":store",builder().s(storeId).build())).build()).build(),TransactWriteItem.builder().delete(Delete.builder().tableName(table).key(skuKey(storeId,product.sku())).build()).build()));}
  public int adjustStock(String id,int delta,long version){return adjustStock(DEFAULT_STORE,id,delta,version);}
  public int adjustStock(String storeId,String id,int delta,long version){int required=Math.max(0,-delta);try{var out=db.updateItem(r->r.tableName(table).key(key(id)).updateExpression("SET stockLevel = stockLevel + :d, #v = #v + :one").conditionExpression("#v = :v AND stockLevel >= :required AND storeId = :store").expressionAttributeNames(Map.of("#v","version")).expressionAttributeValues(Map.of(":d",builder().n(Integer.toString(delta)).build(),":one",builder().n("1").build(),":v",builder().n(Long.toString(version)).build(),":required",builder().n(Integer.toString(required)).build(),":store",builder().s(storeId).build())).returnValues(ReturnValue.ALL_NEW));return Integer.parseInt(out.attributes().get("stockLevel").n());}catch(ConditionalCheckFailedException e){throw new IllegalArgumentException("Insufficient stock, stale product version, or wrong store");}}
  public Map<String,AttributeValue> key(String id){return Map.of("PK",builder().s("PRODUCT#"+id).build(),"SK",builder().s("META").build());}
  private Map<String,AttributeValue> skuKey(String storeId,String sku){return Map.of("PK",builder().s("STORE#"+storeId+"#SKU#"+sku.toUpperCase(Locale.ROOT)).build(),"SK",builder().s("LOCK").build());}
  private Map<String,AttributeValue> skuLock(String storeId,String sku,String id){var item=new HashMap<String,AttributeValue>(skuKey(storeId,sku));item.put("entityType",builder().s("SKU_LOCK").build());item.put("productId",builder().s(id).build());return item;}
  private Map<String,AttributeValue> item(Product p){var m=new HashMap<String,AttributeValue>(key(p.id()));m.put("entityType",builder().s("PRODUCT").build());m.put("id",builder().s(p.id()).build());m.put("storeId",builder().s(p.storeId()).build());m.put("sku",builder().s(p.sku()).build());m.put("name",builder().s(p.name()).build());m.put("category",builder().s(p.category()).build());m.put("supplier",builder().s(p.supplier()).build());m.put("price",builder().n(p.price().toPlainString()).build());m.put("cost",builder().n(p.cost().toPlainString()).build());m.put("stockLevel",builder().n(""+p.stockLevel()).build());m.put("reorderThreshold",builder().n(""+p.reorderThreshold()).build());m.put("leadTimeDays",builder().n(""+p.leadTimeDays()).build());m.put("version",builder().n(""+p.version()).build());return m;}
  private Product from(Map<String,AttributeValue> m){return new Product(m.get("id").s(),m.get("sku").s(),m.get("name").s(),m.get("category").s(),m.get("supplier").s(),new BigDecimal(m.get("price").n()),new BigDecimal(m.get("cost").n()),Integer.parseInt(m.get("stockLevel").n()),Integer.parseInt(m.get("reorderThreshold").n()),Integer.parseInt(m.get("leadTimeDays").n()),Long.parseLong(m.get("version").n()),m.getOrDefault("storeId",builder().s(DEFAULT_STORE).build()).s());}
  public String table(){return table;}
}
