package com.trendify.recommendation;

import java.math.*;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
  public record PricingInput(BigDecimal price,BigDecimal unitCost,double forecastDaily,int stock,int targetStock,BigDecimal competitorPrice) {}
  public record PricingResult(BigDecimal originalPrice,BigDecimal suggestedPrice,BigDecimal estimatedDailyRevenue,String reason) {}
  public PricingResult price(PricingInput i){
    if(i.price.signum()<=0||i.unitCost.signum()<0||i.forecastDaily<0||i.stock<0||i.targetStock<=0)throw new IllegalArgumentException("Invalid pricing inputs");
    double cover=i.forecastDaily==0?999:i.stock/i.forecastDaily;BigDecimal multiplier=cover<7?new BigDecimal("1.05"):cover>30?new BigDecimal("0.95"):BigDecimal.ONE;
    String reason=cover<7?"Low days-of-cover supports a modest increase":cover>30?"Excess days-of-cover supports a modest markdown":"Inventory is near its target range";
    BigDecimal candidate=i.price.multiply(multiplier);if(i.competitorPrice!=null){candidate=candidate.add(i.competitorPrice).divide(BigDecimal.valueOf(2),4,RoundingMode.HALF_UP);reason += "; competitor price moderates the scenario";}
    BigDecimal floor=i.unitCost.multiply(new BigDecimal("1.15"));BigDecimal low=i.price.multiply(new BigDecimal("0.90")),high=i.price.multiply(new BigDecimal("1.10"));candidate=candidate.max(floor).max(low).min(high).setScale(2,RoundingMode.HALF_UP);
    double response=Math.max(.5,1-1.2*(candidate.doubleValue()/i.price.doubleValue()-1));BigDecimal impact=candidate.multiply(BigDecimal.valueOf(i.forecastDaily*response)).setScale(2,RoundingMode.HALF_UP);
    return new PricingResult(i.price,candidate,impact,reason+". Demand response is a documented scenario assumption, not a causal elasticity estimate.");
  }
  public record ReorderInput(double forecastDaily,int leadTimeDays,double serviceFactor,double demandStdDev,int currentStock,int onOrder,BigDecimal unitCost) {}
  public record ReorderResult(int reorderPoint,int recommendedQuantity,String status,BigDecimal estimatedMonthlyHoldingCost,String assumptions) {}
  public ReorderResult reorder(ReorderInput i){double safety=i.serviceFactor*i.demandStdDev*Math.sqrt(i.leadTimeDays);int point=(int)Math.ceil(i.forecastDaily*i.leadTimeDays+safety);int target=(int)Math.ceil(i.forecastDaily*(i.leadTimeDays+14)+safety);int qty=Math.max(0,target-i.currentStock-i.onOrder);String status=i.currentStock<=point?"STOCKOUT_RISK":i.currentStock>target*1.5?"OVERSTOCK":"HEALTHY";BigDecimal hold=i.unitCost.multiply(BigDecimal.valueOf(Math.max(0,i.currentStock))).multiply(new BigDecimal("0.02")).setScale(2,RoundingMode.HALF_UP);return new ReorderResult(point,qty,status,hold,"Safety stock = service factor × demand standard deviation × √lead time; holding cost assumes 2% of unit cost monthly.");}
}
