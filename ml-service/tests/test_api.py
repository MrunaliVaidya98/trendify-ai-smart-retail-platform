from pathlib import Path
from fastapi.testclient import TestClient
import pytest
import app.main as main

client = TestClient(main.app)

def test_health_reports_model_availability(monkeypatch, tmp_path):
    monkeypatch.setattr(main, "MODEL", tmp_path / "missing.joblib")
    assert client.get("/health").json() == {"status": "UP", "modelAvailable": False}
    main.MODEL.touch()
    assert client.get("/health").json()["modelAvailable"] is True

def test_forecast_trains_when_model_is_missing(monkeypatch, tmp_path):
    model = tmp_path / "model.joblib"
    monkeypatch.setattr(main, "MODEL", model)
    calls = []
    def fake_train(data, path): calls.append((data, path)); path.touch(); return {"modelVersion": "v1"}
    monkeypatch.setattr(main, "train", fake_train)
    monkeypatch.setattr(main.joblib, "load", lambda path: {"report": {"modelVersion": "v1", "model": {"mae": 1, "rmse": 2, "wape": .1}}})
    monkeypatch.setattr(main, "forecast", lambda bundle, sku, horizon: [{"date": "2026-10-17", "units": 3.0}] * horizon)
    response = client.post("/forecast", json={"sku": "SKU-X", "horizon": 7})
    assert response.status_code == 200
    assert len(response.json()["predictions"]) == 7
    assert calls == [(main.DATA, model)]

def test_unknown_sku_returns_404(monkeypatch, tmp_path):
    model = tmp_path / "model.joblib"; model.touch(); monkeypatch.setattr(main, "MODEL", model)
    monkeypatch.setattr(main.joblib, "load", lambda path: {"report": {"modelVersion": "v1", "model": {}}})
    monkeypatch.setattr(main, "forecast", lambda *args: (_ for _ in ()).throw(KeyError("UNKNOWN")))
    response = client.post("/forecast", json={"sku": "UNKNOWN", "horizon": 7})
    assert response.status_code == 404

@pytest.mark.parametrize("horizon", [0, 31])
def test_horizon_validation(horizon):
    response = client.post("/forecast", json={"sku": "SKU-X", "horizon": horizon})
    assert response.status_code == 422

def test_empty_sku_is_rejected():
    assert client.post("/forecast", json={"sku": "", "horizon": 7}).status_code == 422

def test_primary_model_failure_uses_seasonal_naive_fallback(monkeypatch, tmp_path):
    model = tmp_path / "broken.joblib"; model.touch(); monkeypatch.setattr(main, "MODEL", model)
    monkeypatch.setattr(main.joblib, "load", lambda path: (_ for _ in ()).throw(ValueError("corrupt model")))
    monkeypatch.setattr(main, "seasonal_naive_forecast", lambda data, sku, horizon: [{"date": "2026-10-17", "units": 4.0}] * horizon)
    response = client.post("/forecast", json={"sku": "SKU-X", "horizon": 7})
    assert response.status_code == 200
    assert response.json()["fallback"] is True
    assert response.json()["modelVersion"] == "seasonal-naive"

def test_model_and_fallback_failure_returns_controlled_503(monkeypatch, tmp_path):
    model = tmp_path / "broken.joblib"; model.touch(); monkeypatch.setattr(main, "MODEL", model)
    monkeypatch.setattr(main.joblib, "load", lambda path: (_ for _ in ()).throw(ValueError("corrupt model")))
    monkeypatch.setattr(main, "seasonal_naive_forecast", lambda *args: (_ for _ in ()).throw(ValueError("bad history")))
    response = client.post("/forecast", json={"sku": "SKU-X", "horizon": 7})
    assert response.status_code == 503
    assert response.json()["detail"] == "Forecast model and fallback are unavailable"
