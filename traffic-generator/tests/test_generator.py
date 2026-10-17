import pathlib, random, sys, pytest
sys.path.insert(0,str(pathlib.Path(__file__).parents[1]))
from config import Config
from generator import TrafficGenerator
from scenarios import choose_event

class Client:
    def __init__(self,fail_order=False): self.events=[]; self.fail_order=fail_order
    def event(self,event): self.events.append(event); return {"created":True}
    def order(self,product):
        if self.fail_order: raise RuntimeError("out of stock")
        return {"id":"order-1"}

def test_seed_makes_event_sequence_deterministic():
    first=random.Random(7); second=random.Random(7)
    assert [choose_event("normal",first) for _ in range(20)]==[choose_event("normal",second) for _ in range(20)]
def test_event_has_required_fields_and_synthetic_label():
    event,product=TrafficGenerator(Client(),7,"normal").next_event([])
    assert event["eventId"] and event["sessionId"] and event["occurredAt"] and event["attributes"]["source"]=="trendify-simulator" and product is None
def test_failed_purchase_is_recorded_without_bypassing_order_rules(monkeypatch):
    client=Client(True); generator=TrafficGenerator(client,1,"normal"); monkeypatch.setattr("generator.choose_event",lambda *_:"PURCHASE_COMPLETED")
    generator.emit_once([{"id":"p1","version":1}])
    assert client.events[0]["eventType"]=="PURCHASE_FAILED" and client.events[0]["attributes"]["reason"]=="RuntimeError"
def test_rate_is_bounded():
    with pytest.raises(ValueError): Config(events_per_second=101).validate()
