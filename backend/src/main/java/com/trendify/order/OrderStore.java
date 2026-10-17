package com.trendify.order;

import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder;
import com.trendify.order.OrderController.*;
import com.trendify.product.ProductController.Product;
import com.trendify.product.ProductStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
public class OrderStore {
  private final DynamoDbClient db;private final ProductStore products;
  public OrderStore(DynamoDbClient db,ProductStore products){this.db=db;this.products=products;}
  public Order create(String storeId,String actor,OrderInput input){
    if(input.lines().stream().map(LineInput::productId).distinct().count()!=input.lines().size())throw new IllegalArgumentException("Duplicate product lines are not allowed");
    String id=UUID.randomUUID().toString();List<OrderLine> lines=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;List<TransactWriteItem> writes=new ArrayList<>();
    for(LineInput line:input.lines()){
      Product product=products.get(storeId,line.productId());BigDecimal lineTotal=product.price().multiply(BigDecimal.valueOf(line.quantity()));total=total.add(lineTotal);lines.add(new OrderLine(product.id(),product.sku(),product.name(),line.quantity(),product.price(),lineTotal));
      writes.add(TransactWriteItem.builder().update(Update.builder().tableName(products.table()).key(products.key(product.id())).updateExpression("SET stockLevel = stockLevel - :q, #v = #v + :one").conditionExpression("storeId = :store AND #v = :version AND stockLevel >= :q").expressionAttributeNames(Map.of("#v","version")).expressionAttributeValues(Map.of(":q",builder().n(Integer.toString(line.quantity())).build(),":one",builder().n("1").build(),":store",builder().s(storeId).build(),":version",builder().n(Long.toString(line.expectedVersion())).build())).build()).build());
    }
    Order order=new Order(id,storeId,"CONFIRMED",actor,Instant.now(),List.copyOf(lines),total);
    writes.add(TransactWriteItem.builder().put(Put.builder().tableName(products.table()).item(item(order)).conditionExpression("attribute_not_exists(PK)").build()).build());
    try{db.transactWriteItems(r->r.transactItems(writes));}catch(TransactionCanceledException e){throw new IllegalArgumentException("Order rejected because inventory is insufficient, stale, or belongs to another store");}
    return order;
  }
  public Order get(String storeId,String id){var out=db.getItem(r->r.tableName(products.table()).key(key(id)).consistentRead(true));if(!out.hasItem()||!storeId.equals(out.item().get("storeId").s()))throw new NoSuchElementException("Order not found");return from(out.item());}
  private Map<String,AttributeValue> key(String id){return Map.of("PK",builder().s("ORDER#"+id).build(),"SK",builder().s("META").build());}
  private Map<String,AttributeValue> item(Order order){Map<String,AttributeValue> item=new HashMap<>(key(order.id()));item.put("entityType",builder().s("ORDER").build());item.put("id",builder().s(order.id()).build());item.put("storeId",builder().s(order.storeId()).build());item.put("status",builder().s(order.status()).build());item.put("createdBy",builder().s(order.createdBy()).build());item.put("createdAt",builder().s(order.createdAt().toString()).build());item.put("total",builder().n(order.total().toPlainString()).build());item.put("lines",builder().l(order.lines().stream().map(this::line).toList()).build());return item;}
  private AttributeValue line(OrderLine line){return builder().m(Map.of("productId",builder().s(line.productId()).build(),"sku",builder().s(line.sku()).build(),"name",builder().s(line.name()).build(),"quantity",builder().n(Integer.toString(line.quantity())).build(),"unitPrice",builder().n(line.unitPrice().toPlainString()).build(),"lineTotal",builder().n(line.lineTotal().toPlainString()).build())).build();}
  private Order from(Map<String,AttributeValue> item){List<OrderLine> lines=item.get("lines").l().stream().map(v->{var m=v.m();return new OrderLine(m.get("productId").s(),m.get("sku").s(),m.get("name").s(),Integer.parseInt(m.get("quantity").n()),new BigDecimal(m.get("unitPrice").n()),new BigDecimal(m.get("lineTotal").n()));}).toList();return new Order(item.get("id").s(),item.get("storeId").s(),item.get("status").s(),item.get("createdBy").s(),Instant.parse(item.get("createdAt").s()),lines,new BigDecimal(item.get("total").n()));}
}
