package com.trendify.product;

import static org.junit.jupiter.api.Assertions.*;
import com.trendify.product.ProductController.ProductInput;
import com.trendify.order.OrderController.*;
import com.trendify.order.OrderStore;
import com.trendify.recommendation.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Testcontainers(disabledWithoutDocker=true)
class ProductStoreIT {
  @Container static final GenericContainer<?> DYNAMO=new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withCommand("-jar","DynamoDBLocal.jar","-inMemory","-sharedDb").withExposedPorts(8000);
  private static DynamoDbClient db;private static ProductStore store;
  @BeforeAll static void setup(){db=DynamoDbClient.builder().region(Region.US_WEST_2).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local","local"))).endpointOverride(URI.create("http://"+DYNAMO.getHost()+":"+DYNAMO.getMappedPort(8000))).build();store=new ProductStore(db,"trendify-test");store.initialize();}
  @AfterAll static void close(){if(db!=null)db.close();}
  private ProductInput input(String sku,int stock){return new ProductInput(sku,"Integration Product","Tests","Fixture Supplier",new BigDecimal("12.50"),new BigDecimal("4.25"),stock,3,4);}
  @Test void productCrudRoundTrip(){
    var created=store.create(input("SKU-CRUD",7));
    assertEquals(1,created.version());
    var persisted=store.get(created.id());
    assertEquals(created.id(),persisted.id());
    assertEquals(created.sku(),persisted.sku());
    assertEquals(created.name(),persisted.name());
    assertEquals(created.category(),persisted.category());
    assertEquals(created.supplier(),persisted.supplier());
    assertEquals(0,created.price().compareTo(persisted.price()));
    assertEquals(0,created.cost().compareTo(persisted.cost()));
    assertEquals(created.stockLevel(),persisted.stockLevel());
    assertEquals(created.reorderThreshold(),persisted.reorderThreshold());
    assertEquals(created.leadTimeDays(),persisted.leadTimeDays());
    assertEquals(created.version(),persisted.version());
    var updated=store.update(created.id(),1,new ProductInput("SKU-CRUD","Updated","Tests","Fixture Supplier",new BigDecimal("13.00"),new BigDecimal("4.25"),7,3,4));
    assertEquals(2,updated.version());
    assertEquals("Updated",store.get(created.id()).name());
    store.delete(created.id());
    assertThrows(NoSuchElementException.class,()->store.get(created.id()));
  }
  @Test void paginationReturnsContinuationWithoutRepeats(){for(int i=0;i<4;i++)store.create(input("SKU-PAGE-"+i,5));var first=store.list(2,null);assertEquals(2,first.items().size());assertNotNull(first.nextToken());var second=store.list(2,first.nextToken());assertEquals(2,second.items().size());var firstIds=first.items().stream().map(ProductController.Product::id).collect(java.util.stream.Collectors.toSet());assertTrue(second.items().stream().noneMatch(p->firstIds.contains(p.id())));assertThrows(IllegalArgumentException.class,()->store.list(2,"not-base64!"));}
  @Test void negativeStockAndStaleVersionAreRejected(){var created=store.create(input("SKU-STOCK",5));assertThrows(IllegalArgumentException.class,()->store.adjustStock(created.id(),-6,1));assertEquals(5,store.get(created.id()).stockLevel());assertEquals(3,store.adjustStock(created.id(),-2,1));assertThrows(IllegalArgumentException.class,()->store.adjustStock(created.id(),-1,1));assertEquals(3,store.get(created.id()).stockLevel());}
  @Test void concurrentWritersCannotBothSucceed(){var created=store.create(input("SKU-CONCURRENT",10));assertEquals(9,store.adjustStock(created.id(),-1,1));assertThrows(IllegalArgumentException.class,()->store.adjustStock(created.id(),-1,1));assertEquals(9,store.get(created.id()).stockLevel());}
  @Test void duplicateSkuIsRejectedWithinStoreButAllowedAcrossStores(){store.create("SEA-01",input("SKU-DUPLICATE",5));assertThrows(IllegalArgumentException.class,()->store.create("SEA-01",input("SKU-DUPLICATE",5)));assertDoesNotThrow(()->store.create("PDX-01",input("SKU-DUPLICATE",5)));}
  @Test void productCannotBeReadAcrossStores(){var created=store.create("PDX-01",input("SKU-PRIVATE",5));assertEquals(created.id(),store.get("PDX-01",created.id()).id());assertThrows(NoSuchElementException.class,()->store.get("SEA-01",created.id()));}
  @Test void orderAtomicallyDecrementsStockAndRejectsStaleConcurrentOrder(){var product=store.create(input("SKU-ORDER",6));var orders=new OrderStore(db,store);var request=new OrderInput(List.of(new LineInput(product.id(),2,product.version())));var order=orders.create("SEA-01","manager",request);assertEquals(0,new BigDecimal("25.00").compareTo(order.total()));assertEquals(4,store.get(product.id()).stockLevel());assertThrows(IllegalArgumentException.class,()->orders.create("SEA-01","manager",request));assertEquals(4,store.get(product.id()).stockLevel());assertEquals(order.id(),orders.get("SEA-01",order.id()).id());assertThrows(NoSuchElementException.class,()->orders.get("PDX-01",order.id()));}
  @Test void pricingDecisionAndAuditSurviveRepositoryRecreation(){var repo=new PriceRecommendationStore(db,store);var scenario=new RecommendationService().price(new RecommendationService.PricingInput(new BigDecimal("10"),new BigDecimal("4"),5,20,20,null));var pending=repo.save(new RecommendationController.SavedRecommendation(UUID.randomUUID().toString(),"SEA-01","product",scenario,"PENDING",null,null));var approved=repo.decide("SEA-01",pending.id(),true,"manager");assertEquals("APPROVED",approved.status());var recreated=new PriceRecommendationStore(db,store);assertEquals("APPROVED",recreated.get("SEA-01",pending.id()).status());assertEquals(1,recreated.history("SEA-01",pending.id()).size());assertThrows(IllegalArgumentException.class,()->recreated.decide("SEA-01",pending.id(),false,"admin"));}
}
