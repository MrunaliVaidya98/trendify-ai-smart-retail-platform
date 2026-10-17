import os
from pathlib import Path
import joblib
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from .pipeline import forecast, seasonal_naive_forecast, train

MODEL = Path(os.getenv("MODEL_PATH", "artifacts/demand_model.joblib"))
DATA = Path(os.getenv("DATASET_PATH", "data/sales.csv"))
app = FastAPI(title="Trendify Demand Forecasting", version="1.0.0")

class ForecastRequest(BaseModel):
    sku: str = Field(min_length=1)
    horizon: int = Field(default=7, ge=1, le=30)

@app.get("/health")
def health(): return {"status": "UP", "modelAvailable": MODEL.exists()}

@app.post("/train")
def train_model(): return train(DATA, MODEL)

@app.post("/forecast")
def create_forecast(req: ForecastRequest):
    try:
        if not MODEL.exists(): train(DATA, MODEL)
        bundle = joblib.load(MODEL)
        predictions = forecast(bundle, req.sku, req.horizon)
        return {"sku": req.sku, "horizon": req.horizon, "modelVersion": bundle["report"]["modelVersion"], "predictions": predictions, "metrics": bundle["report"]["model"], "fallback": False, "limitations": "Forecasts are scenarios from synthetic history; monitor drift before operational use."}
    except KeyError:
        raise HTTPException(404, "Unknown SKU")
    except Exception:
        try:
            predictions = seasonal_naive_forecast(DATA, req.sku, req.horizon)
            return {"sku": req.sku, "horizon": req.horizon, "modelVersion": "seasonal-naive", "predictions": predictions, "metrics": None, "fallback": True, "limitations": "Primary model unavailable; seven-day seasonal-naive fallback is active."}
        except KeyError:
            raise HTTPException(404, "Unknown SKU")
        except Exception as exc:
            raise HTTPException(503, "Forecast model and fallback are unavailable") from exc
