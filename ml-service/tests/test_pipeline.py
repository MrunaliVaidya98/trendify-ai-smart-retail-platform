from pathlib import Path
from app.pipeline import generate_sales, features, forecast, metrics, train
import numpy as np
import pandas as pd
import pandas.testing as pdt
import pytest

def test_features_are_shifted_to_prevent_target_leakage(tmp_path):
    raw = generate_sales(tmp_path / "sales.csv", days=60)
    frame = features(raw); row = frame[frame.sku == "SKU-COFFEE-01"].iloc[0]
    source = raw[raw.sku == "SKU-COFFEE-01"].sort_values("date").reset_index(drop=True)
    assert row.lag_1 == source.iloc[27].units

def test_zero_demand_wape_is_undefined():
    assert metrics(pd.Series([0, 0]), np.array([1, 0])).wape is None

def test_training_is_reproducible_and_persists_metadata(tmp_path):
    data, model = tmp_path / "sales.csv", tmp_path / "model.joblib"
    generate_sales(data, days=120); report = train(data, model)
    assert model.exists() and report["testRows"] > 0
    assert {"mae", "rmse", "wape"} == set(report["model"])

def test_synthetic_generation_is_deterministic(tmp_path):
    first = generate_sales(tmp_path / "first.csv", days=45, seed=7)
    second = generate_sales(tmp_path / "second.csv", days=45, seed=7)
    pdt.assert_frame_equal(first, second)

def test_features_are_isolated_between_skus(tmp_path):
    raw = generate_sales(tmp_path / "sales.csv", days=40)
    frame = features(raw)
    for sku in raw.sku.unique():
        source = raw[raw.sku == sku].sort_values("date").reset_index(drop=True)
        first = frame[frame.sku == sku].iloc[0]
        assert first.lag_1 == source.iloc[27].units
        assert first.rolling_28 == source.iloc[:28].units.mean()

def test_metrics_match_hand_calculation():
    result = metrics(pd.Series([10, 20]), np.array([12, 16]))
    assert result.mae == 3
    assert result.rmse == pytest.approx((20 / 2) ** .5)
    assert result.wape == pytest.approx(6 / 30)

def test_unknown_sku_is_a_cold_start_error(tmp_path):
    history = generate_sales(tmp_path / "sales.csv", days=40)
    with pytest.raises(KeyError): forecast({"history": history, "model": object()}, "UNKNOWN", 7)

def test_recursive_forecast_is_nonnegative_and_horizon_sized(tmp_path):
    class NegativeModel:
        def predict(self, frame): return np.array([-5.0])
    history = generate_sales(tmp_path / "sales.csv", days=40)
    output = forecast({"history": history, "model": NegativeModel()}, "SKU-COFFEE-01", 30)
    assert len(output) == 30
    assert all(point["units"] == 0 for point in output)
    assert len({point["date"] for point in output}) == 30
