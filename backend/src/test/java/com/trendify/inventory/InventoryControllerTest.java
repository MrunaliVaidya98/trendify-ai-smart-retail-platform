package com.trendify.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.trendify.inventory.InventoryController.*;
import com.trendify.product.ProductStore;
import java.security.Principal;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class InventoryControllerTest {
  private ProductStore products;private DynamoDbClient db;private InventoryController controller;private final Principal actor=()->"analyst@trendify.dev";
  @BeforeEach void setup(){products=mock(ProductStore.class);db=mock(DynamoDbClient.class);controller=new InventoryController(products,db,"table");when(products.adjustStock(anyString(),anyString(),anyInt(),anyLong())).thenReturn(8);}
  @Test void stockOutUsesNegativeAbsoluteDelta(){var result=controller.move("p1",new MovementRequest(MovementType.STOCK_OUT,-2,"sale",1),actor);assertEquals(-2,result.delta());assertEquals(8,result.resultingStock());verify(products).adjustStock("SEA-01","p1",-2,1);verify(db).putItem(any(java.util.function.Consumer.class));}
  @Test void stockInUsesPositiveAbsoluteDelta(){var result=controller.move("p1",new MovementRequest(MovementType.STOCK_IN,-4,"delivery",2),actor);assertEquals(4,result.delta());verify(products).adjustStock("SEA-01","p1",4,2);}
  @Test void adjustmentPreservesSign(){controller.move("p1",new MovementRequest(MovementType.ADJUSTMENT,-3,"cycle count",3),actor);verify(products).adjustStock("SEA-01","p1",-3,3);}
  @Test void zeroMovementIsRejectedWithoutWrite(){assertThrows(IllegalArgumentException.class,()->controller.move("p1",new MovementRequest(MovementType.ADJUSTMENT,0,"none",1),actor));verifyNoInteractions(products,db);}
}
