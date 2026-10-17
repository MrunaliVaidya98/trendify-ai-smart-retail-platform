from dataclasses import dataclass
import os

PATTERNS = ("normal", "peak", "flash_sale", "holiday", "low_conversion", "high_abandonment")

@dataclass(frozen=True)
class Config:
    api_url: str = os.getenv("TRENDIFY_API_URL", "http://localhost:8080")
    email: str = os.getenv("TRENDIFY_EMAIL", "manager@trendify.dev")
    password: str = os.getenv("TRENDIFY_PASSWORD", "Trendify123!")
    simulator_key: str = os.getenv("SIMULATOR_API_KEY", "local-simulator-key")
    events_per_second: float = float(os.getenv("EVENTS_PER_SECOND", "1"))
    seed: int = int(os.getenv("SIMULATOR_SEED", "2026"))
    pattern: str = os.getenv("TRAFFIC_PATTERN", "normal")

    def validate(self):
        if not 0.1 <= self.events_per_second <= 100: raise ValueError("EVENTS_PER_SECOND must be between 0.1 and 100")
        if self.pattern not in PATTERNS: raise ValueError(f"TRAFFIC_PATTERN must be one of {', '.join(PATTERNS)}")
