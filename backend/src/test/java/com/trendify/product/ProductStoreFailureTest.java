package com.trendify.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.trendify.product.ProductController.ProductInput;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class ProductStoreFailureTest {
  private ProductInput input(){return new ProductInput("SKU-X","Name","Category","Supplier",new BigDecimal("10"),new BigDecimal("4"),5,2,3);}
  @Test void throttlingIsNotReportedAsSuccess(){var db=mock(DynamoDbClient.class);doThrow(ProvisionedThroughputExceededException.builder().message("throttled").build()).when(db).transactWriteItems(any(java.util.function.Consumer.class));var store=new ProductStore(db,"table");assertThrows(ProvisionedThroughputExceededException.class,()->store.create(input()));}
  @Test void conditionalStockFailureBecomesSafeClientError(){var db=mock(DynamoDbClient.class);doThrow(ConditionalCheckFailedException.builder().message("condition").build()).when(db).updateItem(any(java.util.function.Consumer.class));var store=new ProductStore(db,"table");var error=assertThrows(IllegalArgumentException.class,()->store.adjustStock("id",-6,1));assertTrue(error.getMessage().contains("stale product version"));}
  @Test void staleProductUpdateBecomesSafeClientError(){var db=mock(DynamoDbClient.class);when(db.getItem(any(java.util.function.Consumer.class))).thenReturn(GetItemResponse.builder().item(productItem()).build());doThrow(TransactionCanceledException.builder().message("cancelled").build()).when(db).transactWriteItems(any(java.util.function.Consumer.class));var store=new ProductStore(db,"table");assertThrows(IllegalArgumentException.class,()->store.update("id",99,input()));}
  private Map<String,AttributeValue> productItem(){return Map.ofEntries(Map.entry("PK",s("PRODUCT#id")),Map.entry("SK",s("META")),Map.entry("entityType",s("PRODUCT")),Map.entry("id",s("id")),Map.entry("storeId",s("SEA-01")),Map.entry("sku",s("SKU-X")),Map.entry("name",s("Name")),Map.entry("category",s("Category")),Map.entry("supplier",s("Supplier")),Map.entry("price",n("10")),Map.entry("cost",n("4")),Map.entry("stockLevel",n("5")),Map.entry("reorderThreshold",n("2")),Map.entry("leadTimeDays",n("3")),Map.entry("version",n("1")));}
  private AttributeValue s(String value){return AttributeValue.builder().s(value).build();}private AttributeValue n(String value){return AttributeValue.builder().n(value).build();}
}
