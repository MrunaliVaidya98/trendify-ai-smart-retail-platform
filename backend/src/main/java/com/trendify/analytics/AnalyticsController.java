package com.trendify.analytics;

import java.math.*;
import java.time.*;
import java.util.*;
import com.trendify.security.StoreAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/analytics")
public class AnalyticsController {
  public record Point(LocalDate date,BigDecimal revenue,int units) {}
  public record Dashboard(BigDecimal revenue,int unitsSold,BigDecimal averageOrderValue,BigDecimal grossMargin,List<Point> revenueTrend,int stockoutRisks,int pendingRecommendations,String storeId,String category) {}
  private final StoreAccess access;
  public AnalyticsController(StoreAccess access){this.access=access;}
  public AnalyticsController(){this(new StoreAccess());}
  public Dashboard dashboard(int days){return dashboard(days,null,null,null);}
  @GetMapping("/dashboard") public Dashboard dashboard(@RequestParam(defaultValue="30") int days,@RequestParam(required=false) String storeId,@RequestParam(required=false) String category,Authentication authentication){
    String resolvedStore=access.resolve(authentication,storeId);if(days<7||days>365)throw new IllegalArgumentException("days must be between 7 and 365");if(category!=null&&category.isBlank())throw new IllegalArgumentException("category must not be blank");long scopeSeed=Objects.hash(resolvedStore,category);Random r=new Random(420L+days+scopeSeed);List<Point> trend=new ArrayList<>();BigDecimal revenue=BigDecimal.ZERO;int units=0;
    for(int i=days-1;i>=0;i--){int u=34+r.nextInt(35)+(i%7<2?8:0);BigDecimal rev=BigDecimal.valueOf(u).multiply(new BigDecimal("12.47"));trend.add(new Point(LocalDate.now().minusDays(i),rev,u));revenue=revenue.add(rev);units+=u;}
    return new Dashboard(revenue,units,revenue.divide(BigDecimal.valueOf(days*9L),2,RoundingMode.HALF_UP),revenue.multiply(new BigDecimal("0.58")).setScale(2,RoundingMode.HALF_UP),trend,Math.floorMod(Objects.hash(resolvedStore,category),4),Math.floorMod(Objects.hash(category,resolvedStore),5),resolvedStore,category);
  }
}
