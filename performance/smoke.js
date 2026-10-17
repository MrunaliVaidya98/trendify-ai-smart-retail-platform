import http from 'k6/http';
import {check, sleep} from 'k6';
import {Rate, Trend} from 'k6/metrics';

const failures=new Rate('business_failures');
const dashboardDuration=new Trend('dashboard_duration',true);
export const options={vus:Number(__ENV.VUS||5),duration:__ENV.DURATION||'30s',thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<500'],dashboard_duration:['p(95)<500'],business_failures:['rate<0.01']}};
const base=__ENV.BASE_URL||'http://host.docker.internal:4200';

export function setup(){
  const response=http.post(`${base}/api/auth/login`,JSON.stringify({email:'admin@trendify.dev',password:'Trendify123!'}),{headers:{'Content-Type':'application/json'}});
  const ok=check(response,{'login status is 200':r=>r.status===200,'login returns token':r=>Boolean(r.json('accessToken'))});
  if(!ok) throw new Error(`Login failed: ${response.status}`);
  return {token:response.json('accessToken')};
}

export default function(data){
  const response=http.get(`${base}/api/analytics/dashboard?days=30`,{headers:{Authorization:`Bearer ${data.token}`}});
  dashboardDuration.add(response.timings.duration);
  const ok=check(response,{'dashboard status is 200':r=>r.status===200,'dashboard has 30 points':r=>r.json('revenueTrend').length===30,'dashboard has nonnegative revenue':r=>r.json('revenue')>=0});
  failures.add(!ok);sleep(1);
}
