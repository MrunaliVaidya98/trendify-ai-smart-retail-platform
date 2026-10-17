import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import pytest

spec = importlib.util.spec_from_file_location("trendify_lambda", Path(__file__).parents[2] / "lambda" / "handler.py")
lambda_module = importlib.util.module_from_spec(spec); spec.loader.exec_module(lambda_module)

class Response:
    def __init__(self, payload): self.payload = payload
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def read(self): return json.dumps(self.payload).encode()

def test_scheduled_event_invokes_training_and_preserves_event_id(monkeypatch):
    monkeypatch.setenv("ML_BATCH_URL", "https://ml.internal/")
    observed = {}
    def open_request(request, timeout): observed.update(url=request.full_url, method=request.method, body=request.data, timeout=timeout); return Response({"modelVersion": "v-123"})
    monkeypatch.setattr(lambda_module.urllib.request, "urlopen", open_request)
    result = lambda_module.handler({"id": "event-1"}, SimpleNamespace(aws_request_id="request-1"))
    assert result["statusCode"] == 200 and result["runId"] == "event-1"
    assert observed == {"url": "https://ml.internal/train", "method": "POST", "body": b"{}", "timeout": 25}

def test_missing_event_id_uses_request_id(monkeypatch):
    monkeypatch.setenv("ML_BATCH_URL", "https://ml.internal")
    monkeypatch.setattr(lambda_module.urllib.request, "urlopen", lambda *args, **kwargs: Response({"modelVersion": "v1"}))
    assert lambda_module.handler({}, SimpleNamespace(aws_request_id="request-9"))["runId"] == "request-9"

def test_upstream_failure_is_reraised_for_eventbridge_retry(monkeypatch):
    monkeypatch.setenv("ML_BATCH_URL", "https://ml.internal")
    monkeypatch.setattr(lambda_module.urllib.request, "urlopen", lambda *args, **kwargs: (_ for _ in ()).throw(TimeoutError("timeout")))
    with pytest.raises(TimeoutError): lambda_module.handler({"id": "event-2"}, SimpleNamespace(aws_request_id="request-2"))

class FakeDynamo:
    def __init__(self, duplicate=False): self.duplicate=duplicate; self.put=[]; self.updated=[]
    def put_item(self, **kwargs):
        if self.duplicate:
            class ConditionalCheckFailedException(Exception): pass
            raise ConditionalCheckFailedException()
        self.put.append(kwargs)
    def get_item(self, **kwargs): return {"Item":{"status":{"S":"COMPLETED"},"result":{"S":json.dumps({"modelVersion":"persisted-v1"})}}}
    def update_item(self, **kwargs): self.updated.append(kwargs)

class FakeS3:
    def __init__(self): self.put=[]
    def put_object(self, **kwargs): self.put.append(kwargs)

def test_persistent_run_claim_writes_result_to_dynamodb_and_s3(monkeypatch):
    monkeypatch.setenv("ML_BATCH_URL", "https://ml.internal");monkeypatch.setenv("TABLE_NAME", "trendify");monkeypatch.setenv("MODEL_BUCKET", "models")
    ddb,s3=FakeDynamo(),FakeS3();monkeypatch.setattr(lambda_module,"_clients",lambda:(ddb,s3));monkeypatch.setattr(lambda_module.urllib.request,"urlopen",lambda *args,**kwargs:Response({"modelVersion":"v2"}))
    result=lambda_module.handler({"id":"event-persist"},SimpleNamespace(aws_request_id="request"))
    assert result["duplicate"] is False and len(ddb.put)==1 and len(ddb.updated)==1 and len(s3.put)==1
    assert s3.put[0]["Key"]=="forecast-runs/event-persist.json"

def test_duplicate_completed_event_returns_persisted_result_without_training(monkeypatch):
    monkeypatch.setenv("ML_BATCH_URL", "https://ml.internal");monkeypatch.setenv("TABLE_NAME", "trendify");monkeypatch.setenv("MODEL_BUCKET", "models")
    monkeypatch.setattr(lambda_module,"_clients",lambda:(FakeDynamo(duplicate=True),FakeS3()))
    monkeypatch.setattr(lambda_module.urllib.request,"urlopen",lambda *args,**kwargs:pytest.fail("training must not repeat"))
    result=lambda_module.handler({"id":"event-persist"},SimpleNamespace(aws_request_id="request"))
    assert result=={"statusCode":200,"runId":"event-persist","duplicate":True,"result":{"modelVersion":"persisted-v1"}}
