package com.trendify;

import static org.junit.jupiter.api.Assertions.*;
import com.trendify.analytics.AnalyticsController;
import java.math.*;
import org.junit.jupiter.api.Test;

class AnalyticsControllerTest {
  private final AnalyticsController controller=new AnalyticsController();
  @Test void kpisMatchTrend(){var result=controller.dashboard(30);var revenue=result.revenueTrend().stream().map(AnalyticsController.Point::revenue).reduce(BigDecimal.ZERO,BigDecimal::add);var units=result.revenueTrend().stream().mapToInt(AnalyticsController.Point::units).sum();assertEquals(revenue,result.revenue());assertEquals(units,result.unitsSold());assertEquals(result.revenue().multiply(new BigDecimal("0.58")).setScale(2,RoundingMode.HALF_UP),result.grossMargin());assertEquals(30,result.revenueTrend().size());}
  @Test void outputIsDeterministicForSameWindow(){assertEquals(controller.dashboard(7).revenue(),controller.dashboard(7).revenue());}
  @Test void daysRangeIsEnforced(){assertDoesNotThrow(()->controller.dashboard(7));assertDoesNotThrow(()->controller.dashboard(365));assertThrows(IllegalArgumentException.class,()->controller.dashboard(6));assertThrows(IllegalArgumentException.class,()->controller.dashboard(366));}
  @Test void storeAndCategoryFiltersChangeScopeDeterministically(){var sea=controller.dashboard(30,"SEA-01","Beverages",null);var pdx=controller.dashboard(30,"PDX-01","Beverages",null);assertEquals("SEA-01",sea.storeId());assertEquals("Beverages",sea.category());assertNotEquals(sea.revenue(),pdx.revenue());assertEquals(sea,controller.dashboard(30,"SEA-01","Beverages",null));}
}
