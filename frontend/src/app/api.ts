export const API='/api';

export class ApiError extends Error{constructor(public status:number,message:string){super(message)}}

export async function api<T>(path:string,init:RequestInit={}):Promise<T>{
  const token=localStorage.getItem('token');
  const headers=new Headers(init.headers);
  if(token)headers.set('Authorization',`Bearer ${token}`);
  if(init.body)headers.set('Content-Type','application/json');
  const response=await fetch(`${API}${path}`,{...init,headers});
  if(response.status===401&&!path.endsWith('/auth/login')){
    localStorage.removeItem('token');localStorage.removeItem('storeId');window.dispatchEvent(new Event('trendify:unauthorized'));
  }
  if(!response.ok)throw new ApiError(response.status,`Request failed (${response.status})`);
  return response.json() as Promise<T>;
}
