from __future__ import annotations
import json
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error

FEATURES = ["lag_1", "lag_7", "lag_14", "rolling_7", "rolling_28", "dow", "month", "price", "promotion"]

@dataclass
class Metrics:
    mae: float
    rmse: float
    wape: float | None

def generate_sales(path: Path, days: int = 540, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    dates = pd.date_range(end="2026-09-30", periods=days)
    rows = []
    for sku, base, price in [("SKU-COFFEE-01", 21, 5.99), ("SKU-TOTE-01", 7, 18.0), ("SKU-CANDLE-01", 11, 28.0)]:
        for i, date in enumerate(dates):
            promo = int(i % 47 in range(4))
            weekly = 1.25 if date.dayofweek >= 5 else 1.0
            annual = 1 + .18 * np.sin(2 * np.pi * date.dayofyear / 365)
            units = max(0, int(rng.poisson(base * weekly * annual * (1.30 if promo else 1))))
            rows.append((date, sku, units, price * (.9 if promo else 1), promo, "SEA-01"))
    df = pd.DataFrame(rows, columns=["date", "sku", "units", "price", "promotion", "store_id"])
    path.parent.mkdir(parents=True, exist_ok=True); df.to_csv(path, index=False)
    return df

def features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy().sort_values(["sku", "date"]); df["date"] = pd.to_datetime(df["date"])
    grouped = df.groupby("sku", group_keys=False)["units"]
    for lag in (1, 7, 14): df[f"lag_{lag}"] = grouped.shift(lag)
    df["rolling_7"] = grouped.transform(lambda x: x.shift(1).rolling(7).mean())
    df["rolling_28"] = grouped.transform(lambda x: x.shift(1).rolling(28).mean())
    df["dow"] = df.date.dt.dayofweek; df["month"] = df.date.dt.month
    return df.dropna().reset_index(drop=True)

def metrics(y: pd.Series, prediction: np.ndarray) -> Metrics:
    denom = float(np.abs(y).sum())
    return Metrics(float(mean_absolute_error(y, prediction)), float(mean_squared_error(y, prediction) ** .5), float(np.abs(y - prediction).sum() / denom) if denom else None)

def train(data_path: Path, model_path: Path) -> dict:
    raw = pd.read_csv(data_path) if data_path.exists() else generate_sales(data_path)
    frame = features(raw); cutoff = frame.date.quantile(.8)
    train_df, test_df = frame[frame.date <= cutoff], frame[frame.date > cutoff]
    model = HistGradientBoostingRegressor(max_iter=180, learning_rate=.06, max_leaf_nodes=20, random_state=42).fit(train_df[FEATURES], train_df.units)
    pred = np.maximum(0, model.predict(test_df[FEATURES])); baseline = test_df.lag_7.to_numpy()
    report = {"modelVersion": datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"), "trainedAt": datetime.now(timezone.utc).isoformat(), "cutoff": str(cutoff.date()), "testRows": len(test_df), "model": asdict(metrics(test_df.units, pred)), "seasonalNaive": asdict(metrics(test_df.units, baseline)), "syntheticDataWarning": "Metrics from synthetic data do not represent real-world performance."}
    model_path.parent.mkdir(parents=True, exist_ok=True); joblib.dump({"model": model, "report": report, "history": raw}, model_path)
    model_path.with_suffix(".metrics.json").write_text(json.dumps(report, indent=2))
    return report

def forecast(bundle: dict, sku: str, horizon: int) -> list[dict]:
    history = bundle["history"]; rows = history[history.sku == sku].copy().sort_values("date")
    if rows.empty: raise KeyError(sku)
    rows["date"] = pd.to_datetime(rows.date); output = []
    for _ in range(horizon):
        date = rows.date.max() + pd.Timedelta(days=1); recent = rows.units.to_numpy(); last = rows.iloc[-1]
        vector = pd.DataFrame([{"lag_1": recent[-1], "lag_7": recent[-7], "lag_14": recent[-14], "rolling_7": recent[-7:].mean(), "rolling_28": recent[-28:].mean(), "dow": date.dayofweek, "month": date.month, "price": last.price, "promotion": 0}])
        value = max(0., float(bundle["model"].predict(vector[FEATURES])[0])); output.append({"date": date.date().isoformat(), "units": round(value, 2)})
        rows = pd.concat([rows, pd.DataFrame([{"date": date, "sku": sku, "units": value, "price": last.price, "promotion": 0, "store_id": last.store_id}])], ignore_index=True)
    return output

def seasonal_naive_forecast(data_path: Path, sku: str, horizon: int) -> list[dict]:
    history = pd.read_csv(data_path)
    rows = history[history.sku == sku].copy().sort_values("date")
    if rows.empty: raise KeyError(sku)
    if len(rows) < 7: raise ValueError("At least seven observations are required for fallback forecasting")
    rows["date"] = pd.to_datetime(rows.date)
    weekly = rows.units.tail(7).astype(float).to_list()
    last_date = rows.date.max()
    return [{"date": (last_date + pd.Timedelta(days=offset + 1)).date().isoformat(), "units": round(max(0., weekly[offset % 7]), 2)} for offset in range(horizon)]
