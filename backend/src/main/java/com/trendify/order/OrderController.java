package com.trendify.order;

import com.trendify.security.StoreAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/orders")
public class OrderController {
  public record LineInput(@NotBlank String productId,@Min(1) @Max(1000) int quantity,@Positive long expectedVersion){}
  public record OrderInput(@NotEmpty @Size(max=20) List<@Valid LineInput> lines){}
  public record OrderLine(String productId,String sku,String name,int quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
  public record Order(String id,String storeId,String status,String createdBy,Instant createdAt,List<OrderLine> lines,BigDecimal total){}
  private final OrderStore orders;private final StoreAccess access;
  public OrderController(OrderStore orders,StoreAccess access){this.orders=orders;this.access=access;}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')") public Order create(@Valid @RequestBody OrderInput input,@RequestParam(required=false) String storeId,Authentication auth){return orders.create(access.resolve(auth,storeId),auth.getName(),input);}
  @GetMapping("/{id}") public Order get(@PathVariable String id,@RequestParam(required=false) String storeId,Authentication auth){return orders.get(access.resolve(auth,storeId),id);}
}
