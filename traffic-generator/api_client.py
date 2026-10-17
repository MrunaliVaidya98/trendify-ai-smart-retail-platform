import json, time, urllib.error, urllib.request

class ApiError(RuntimeError): pass

class TrendifyClient:
    def __init__(self, base_url, simulator_key, retries=3): self.base_url=base_url.rstrip("/"); self.simulator_key=simulator_key; self.retries=retries; self.token=""
    def _request(self, method, path, body=None, simulator=False):
        data=None if body is None else json.dumps(body).encode(); headers={"Content-Type":"application/json"}
        if self.token: headers["Authorization"]=f"Bearer {self.token}"
        if simulator: headers["X-Simulator-Key"]=self.simulator_key
        request=urllib.request.Request(self.base_url+path, data=data, headers=headers, method=method)
        for attempt in range(self.retries):
            try:
                with urllib.request.urlopen(request, timeout=5) as response:
                    payload=response.read(); return json.loads(payload) if payload else None
            except urllib.error.HTTPError as exc:
                if exc.code < 500 or attempt == self.retries-1: raise ApiError(f"{method} {path} failed with HTTP {exc.code}") from exc
            except (urllib.error.URLError, TimeoutError) as exc:
                if attempt == self.retries-1: raise ApiError(f"{method} {path} unavailable") from exc
            time.sleep(.2*(2**attempt))
    def login(self,email,password): self.token=self._request("POST","/api/auth/login",{"email":email,"password":password})["accessToken"]
    def products(self): return self._request("GET","/api/products?limit=100")["items"]
    def event(self,payload): return self._request("POST","/api/v1/events/traffic",payload,simulator=True)
    def order(self,product): return self._request("POST","/api/orders",{"lines":[{"productId":product["id"],"quantity":1,"expectedVersion":product["version"]}]})
