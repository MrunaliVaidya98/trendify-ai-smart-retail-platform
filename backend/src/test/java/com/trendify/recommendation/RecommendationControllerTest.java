package com.trendify.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import com.trendify.recommendation.RecommendationService.PricingInput;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class RecommendationControllerTest {
  private final RecommendationController controller=new RecommendationController(new RecommendationService());
  private final PricingInput input=new PricingInput(new BigDecimal("20"),new BigDecimal("5"),5,10,20,null);
  @Test void recommendationStartsPendingAndCanBeApprovedWithAuditActor(){var pending=controller.price("product-1",input);assertEquals("PENDING",pending.status());assertNull(pending.decidedBy());Principal manager=()->"manager@trendify.dev";var approved=controller.decide(pending.id(),true,manager);assertEquals("APPROVED",approved.status());assertEquals(manager.getName(),approved.decidedBy());assertNotNull(approved.decidedAt());}
  @Test void recommendationCanBeRejected(){var pending=controller.price("product-1",input);assertEquals("REJECTED",controller.decide(pending.id(),false,()->"admin@trendify.dev").status());}
  @Test void unknownRecommendationCannotBeDecided(){assertThrows(NoSuchElementException.class,()->controller.decide("missing",true,()->"admin"));}
}
