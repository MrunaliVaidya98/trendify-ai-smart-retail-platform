from datetime import datetime, timezone
import random, time, uuid
from scenarios import choose_event

class TrafficGenerator:
    def __init__(self,client,seed,pattern,sleep=time.sleep): self.client=client; self.rng=random.Random(seed); self.pattern=pattern; self.sleep=sleep
    def next_event(self,products=None):
        event_type=choose_event(self.pattern,self.rng)
        product=self.rng.choice(products) if products and event_type in {"PRODUCT_VIEW","ADD_TO_CART","CHECKOUT_STARTED","PURCHASE_COMPLETED","PURCHASE_FAILED"} else None
        return {"eventId":str(uuid.UUID(int=self.rng.getrandbits(128))),"sessionId":f"sim-{self.rng.randrange(1,10001)}","eventType":event_type,"productId":product["id"] if product else None,"occurredAt":datetime.now(timezone.utc).isoformat(),"attributes":{"source":"trendify-simulator","pattern":self.pattern}},product
    def emit_once(self,products=None):
        event,product=self.next_event(products)
        if event["eventType"]=="PURCHASE_COMPLETED" and product:
            try: event["attributes"]["orderId"]=self.client.order(product)["id"]
            except Exception as exc: event["eventType"]="PURCHASE_FAILED"; event["attributes"]["reason"]=type(exc).__name__
        return self.client.event(event)
    def run(self,events_per_second):
        products=self.client.products()
        while True: self.emit_once(products); self.sleep(1/events_per_second)
