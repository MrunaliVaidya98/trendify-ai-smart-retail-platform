package com.trendify.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.trendify.security.StoreAccess;

@RestController @RequestMapping("/api/products")
public class ProductController {
  public record Product(String id,String sku,String name,String category,String supplier,BigDecimal price,BigDecimal cost,int stockLevel,int reorderThreshold,int leadTimeDays,long version,String storeId) {}
  public record ProductInput(@NotBlank String sku,@NotBlank String name,@NotBlank String category,@NotBlank String supplier,@DecimalMin("0.01") BigDecimal price,@DecimalMin("0.00") BigDecimal cost,@PositiveOrZero int stockLevel,@PositiveOrZero int reorderThreshold,@Positive int leadTimeDays) {}
  public record Page(List<Product> items,String nextToken) {}
  private final ProductStore store;private final StoreAccess access;
  @org.springframework.beans.factory.annotation.Autowired public ProductController(ProductStore store,StoreAccess access){this.store=store;this.access=access;}
  ProductController(ProductStore store){this(store,new StoreAccess());}
  @GetMapping public Page list(@RequestParam(defaultValue="25") @Min(1) @Max(100) int limit,@RequestParam(required=false) String nextToken,@RequestParam(required=false) String storeId,@RequestParam(required=false) String category,Authentication auth){return store.list(access.resolve(auth,storeId),category,limit,nextToken);}
  @GetMapping("/{id}") public Product get(@PathVariable String id,@RequestParam(required=false) String storeId,Authentication auth){return store.get(access.resolve(auth,storeId),id);}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')") public Product create(@Valid @RequestBody ProductInput input,@RequestParam(required=false) String storeId,Authentication auth){return store.create(access.resolve(auth,storeId),input);}
  @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')") public Product update(@PathVariable String id,@RequestParam long expectedVersion,@RequestParam(required=false) String storeId,@Valid @RequestBody ProductInput input,Authentication auth){return store.update(access.resolve(auth,storeId),id,expectedVersion,input);}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable String id,@RequestParam(required=false) String storeId,Authentication auth){store.delete(access.resolve(auth,storeId),id);}
}
