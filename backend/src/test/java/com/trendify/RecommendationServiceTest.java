package com.trendify;

import static org.junit.jupiter.api.Assertions.*;
import com.trendify.recommendation.RecommendationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
  private final RecommendationService service=new RecommendationService();
  @Test void neverPricesBelowMarginFloor(){var out=service.price(new RecommendationService.PricingInput(new BigDecimal("10"),new BigDecimal("9"),10,500,100,null));assertTrue(out.suggestedPrice().compareTo(new BigDecimal("10.35"))>=0);}
  @Test void reorderFlagsRiskAndAccountsForOnOrder(){var out=service.reorder(new RecommendationService.ReorderInput(10,7,1.65,3,20,40,new BigDecimal("4")));assertEquals("STOCKOUT_RISK",out.status());assertTrue(out.recommendedQuantity()>0);}
  @Test void lowCoverIncreaseNeverExceedsTenPercent(){var out=service.price(new RecommendationService.PricingInput(new BigDecimal("100"),new BigDecimal("30"),20,10,100,null));assertEquals(new BigDecimal("105.00"),out.suggestedPrice());assertTrue(out.reason().contains("not a causal elasticity"));}
  @Test void highCoverMarkdownNeverExceedsTenPercent(){var out=service.price(new RecommendationService.PricingInput(new BigDecimal("100"),new BigDecimal("10"),1,100,25,null));assertTrue(out.suggestedPrice().compareTo(new BigDecimal("90.00"))>=0);}
  @Test void competitorPriceModeratesScenario(){var out=service.price(new RecommendationService.PricingInput(new BigDecimal("100"),new BigDecimal("20"),20,10,100,new BigDecimal("80")));assertEquals(new BigDecimal("92.50"),out.suggestedPrice());assertTrue(out.reason().contains("competitor price"));}
  @Test void invalidPricingInputsAreRejected(){assertThrows(IllegalArgumentException.class,()->service.price(new RecommendationService.PricingInput(BigDecimal.ZERO,BigDecimal.ONE,1,1,1,null)));}
  @Test void reorderFormulaIncludesLeadTimeAndSafetyStock(){var out=service.reorder(new RecommendationService.ReorderInput(10,4,2,3,200,0,new BigDecimal("5")));assertEquals(52,out.reorderPoint());assertEquals("HEALTHY",out.status());assertEquals(new BigDecimal("20.00"),out.estimatedMonthlyHoldingCost());}
  @Test void excessiveInventoryIsFlaggedAsOverstock(){var out=service.reorder(new RecommendationService.ReorderInput(10,4,2,3,400,0,new BigDecimal("5")));assertEquals("OVERSTOCK",out.status());assertEquals(0,out.recommendedQuantity());}
  @Test void healthyInventoryRequiresNoOrderWhenTargetCovered(){var out=service.reorder(new RecommendationService.ReorderInput(2,2,1,0,40,10,new BigDecimal("3")));assertEquals(0,out.recommendedQuantity());assertEquals("HEALTHY",out.status());}
}
