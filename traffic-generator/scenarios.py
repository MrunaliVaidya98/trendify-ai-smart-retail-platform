from dataclasses import dataclass
import random

@dataclass(frozen=True)
class Scenario:
    view: float; search: float; cart: float; checkout: float; purchase: float; abandon: float; fail: float

SCENARIOS = {
    "normal": Scenario(.31,.12,.18,.10,.07,.15,.07), "peak": Scenario(.27,.10,.20,.13,.12,.11,.07),
    "flash_sale": Scenario(.22,.08,.23,.16,.16,.09,.06), "holiday": Scenario(.25,.10,.21,.14,.13,.10,.07),
    "low_conversion": Scenario(.40,.16,.14,.07,.02,.15,.06), "high_abandonment": Scenario(.25,.10,.18,.09,.03,.29,.06),
}
EVENTS = ("PRODUCT_VIEW","SEARCH","ADD_TO_CART","CHECKOUT_STARTED","PURCHASE_COMPLETED","CART_ABANDONED","PURCHASE_FAILED")

def choose_event(pattern: str, rng: random.Random) -> str:
    return rng.choices(EVENTS, weights=tuple(SCENARIOS[pattern].__dict__.values()), k=1)[0]
