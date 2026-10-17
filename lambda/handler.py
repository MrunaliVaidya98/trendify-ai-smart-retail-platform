import json, logging, os, urllib.request
from datetime import datetime, timezone
try:
    import boto3
except ImportError:  # Local unit tests can inject clients without AWS dependencies.
    boto3 = None

log=logging.getLogger();log.setLevel(logging.INFO)

def _clients():
    if boto3 is None: raise RuntimeError("boto3 is required when persistence is configured")
    return boto3.client("dynamodb"),boto3.client("s3")

def _existing(ddb,table,run_id):
    response=ddb.get_item(TableName=table,Key={"PK":{"S":f"FORECAST_RUN#{run_id}"},"SK":{"S":"META"}},ConsistentRead=True)
    item=response.get("Item")
    if not item or item.get("status",{}).get("S")!="COMPLETED": return None
    return json.loads(item["result"]["S"])

def handler(event, context):
    run_id=event.get("id",context.aws_request_id);table=os.getenv("TABLE_NAME");bucket=os.getenv("MODEL_BUCKET");ddb=s3=None
    if table and bucket:
        ddb,s3=_clients();now=datetime.now(timezone.utc).isoformat()
        try:
            ddb.put_item(TableName=table,Item={"PK":{"S":f"FORECAST_RUN#{run_id}"},"SK":{"S":"META"},"entityType":{"S":"FORECAST_RUN"},"status":{"S":"RUNNING"},"startedAt":{"S":now}},ConditionExpression="attribute_not_exists(PK)")
        except Exception as exc:
            if type(exc).__name__!="ConditionalCheckFailedException": raise
            existing=_existing(ddb,table,run_id)
            if existing is None: raise RuntimeError("Forecast run is already in progress") from exc
            return {"statusCode":200,"runId":run_id,"duplicate":True,"result":existing}
    request=urllib.request.Request(os.environ["ML_BATCH_URL"].rstrip("/")+"/train",data=b"{}",headers={"Content-Type":"application/json"},method="POST")
    try:
        with urllib.request.urlopen(request,timeout=25) as response: result=json.loads(response.read())
        completed=datetime.now(timezone.utc).isoformat()
        if ddb and s3:
            key=f"forecast-runs/{run_id}.json";payload=json.dumps(result,separators=(",",":"),sort_keys=True).encode()
            s3.put_object(Bucket=bucket,Key=key,Body=payload,ContentType="application/json",ServerSideEncryption="AES256")
            ddb.update_item(TableName=table,Key={"PK":{"S":f"FORECAST_RUN#{run_id}"},"SK":{"S":"META"}},UpdateExpression="SET #status=:completed, completedAt=:at, #result=:result, resultKey=:key",ExpressionAttributeNames={"#status":"status","#result":"result"},ExpressionAttributeValues={":completed":{"S":"COMPLETED"},":at":{"S":completed},":result":{"S":json.dumps(result)},":key":{"S":key},":running":{"S":"RUNNING"}},ConditionExpression="#status=:running")
        log.info(json.dumps({"event":"forecast_refresh_complete","runId":run_id,"modelVersion":result["modelVersion"]}))
        return {"statusCode":200,"runId":run_id,"duplicate":False,"completedAt":completed,"result":result}
    except Exception as exc:
        if ddb:
            try: ddb.update_item(TableName=table,Key={"PK":{"S":f"FORECAST_RUN#{run_id}"},"SK":{"S":"META"}},UpdateExpression="SET #status=:failed, errorType=:error",ExpressionAttributeNames={"#status":"status"},ExpressionAttributeValues={":failed":{"S":"FAILED"},":error":{"S":type(exc).__name__}})
            except Exception: log.exception("Unable to persist failed forecast run")
        log.exception(json.dumps({"event":"forecast_refresh_failed","runId":run_id,"errorType":type(exc).__name__}))
        raise
