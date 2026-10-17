package com.trendify.inventory;

import com.trendify.product.ProductStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.trendify.security.StoreAccess;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@RestController @RequestMapping("/api/inventory")
public class InventoryController {
  public enum MovementType { STOCK_IN, STOCK_OUT, ADJUSTMENT }
  public record MovementRequest(@NotNull MovementType type,@NotNull @Min(-100000) @Max(100000) Integer quantity,@NotBlank String reason,@Positive long expectedVersion) {}
  public record Movement(String id,String productId,MovementType type,int delta,String reason,String actor,Instant createdAt,int resultingStock) {}
  private final ProductStore products;private final DynamoDbClient db;private final String table;private final StoreAccess access;
  @org.springframework.beans.factory.annotation.Autowired public InventoryController(ProductStore products,DynamoDbClient db,@org.springframework.beans.factory.annotation.Value("${trendify.aws.table-name}") String table,StoreAccess access){this.products=products;this.db=db;this.table=table;this.access=access;}
  InventoryController(ProductStore products,DynamoDbClient db,String table){this(products,db,table,new StoreAccess());}
  @PostMapping("/{productId}/movements") @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_ANALYST')")
  public Movement move(@PathVariable String productId,@Valid @RequestBody MovementRequest r,@RequestParam(required=false) String storeId,Authentication principal){return moveForStore(productId,r,access.resolve(principal,storeId),principal);}
  Movement move(String productId,MovementRequest r,java.security.Principal principal){return moveForStore(productId,r,"SEA-01",principal);}
  private Movement moveForStore(String productId,MovementRequest r,String storeId,java.security.Principal principal){int delta=switch(r.type()){case STOCK_IN->Math.abs(r.quantity());case STOCK_OUT->-Math.abs(r.quantity());case ADJUSTMENT->r.quantity();};if(delta==0)throw new IllegalArgumentException("Quantity must not be zero");int stock=products.adjustStock(storeId,productId,delta,r.expectedVersion());Movement m=new Movement(UUID.randomUUID().toString(),productId,r.type(),delta,r.reason(),principal.getName(),Instant.now(),stock);Map<String,AttributeValue> item=new HashMap<>();item.put("PK",av("PRODUCT#"+productId));item.put("SK",av("MOVEMENT#"+m.createdAt()+"#"+m.id()));item.put("entityType",av("MOVEMENT"));item.put("storeId",av(storeId));item.put("payload",av(m.type()+"|"+delta+"|"+m.reason()+"|"+m.actor()+"|"+stock));db.putItem(x->x.tableName(table).item(item));return m;}
  private AttributeValue av(String s){return AttributeValue.builder().s(s).build();}
}
