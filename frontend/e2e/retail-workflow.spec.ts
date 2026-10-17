import {expect, test,type APIRequestContext} from '@playwright/test';

async function productBySku(request:APIRequestContext,headers:{Authorization:string},sku:string){let nextToken='';do{const params=new URLSearchParams({limit:'100',storeId:'SEA-01'});if(nextToken)params.set('nextToken',nextToken);const response=await request.get(`/api/products?${params}`,{headers});expect(response.ok()).toBeTruthy();const page=await response.json();const product=page.items.find((item:{sku:string})=>item.sku===sku);if(product)return product;nextToken=page.nextToken??''}while(nextToken);throw new Error(`Product ${sku} was not found`)}

test('demo login renders live KPI and inventory data',async({page})=>{
  await page.goto('/login');
  await page.getByLabel('Email').fill('admin@trendify.dev');
  await page.getByLabel('Password').fill('Trendify123!');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page.getByText('NET REVENUE',{exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Inventory watchlist'})).toBeVisible();
  expect(await page.locator('tbody tr').count()).toBeGreaterThanOrEqual(3);
});

test('inventory stock-in is reflected by the dashboard and is cleaned up',async({page,request})=>{
  const login=await request.post('/api/auth/login',{data:{email:'admin@trendify.dev',password:'Trendify123!'}});expect(login.ok()).toBeTruthy();const token=(await login.json()).accessToken;const headers={Authorization:`Bearer ${token}`};
  const product=await productBySku(request,headers,'SKU-COFFEE-01');
  const stockIn=await request.post(`/api/inventory/${product.id}/movements`,{headers,data:{type:'STOCK_IN',quantity:1,reason:'Playwright isolated fixture',expectedVersion:product.version}});expect(stockIn.ok()).toBeTruthy();
  try{
    await page.goto('/login');
    await page.evaluate((value)=>localStorage.setItem('token',value),token);
    await page.goto('/');
    await page.getByLabel('Category').selectOption('Beverages');
    const row=page.locator('tbody tr').filter({hasText:'SKU-COFFEE-01'});await expect(row).toContainText(String(product.stockLevel+1));
  }finally{
    const cleanup=await request.post(`/api/inventory/${product.id}/movements`,{headers,data:{type:'ADJUSTMENT',quantity:-1,reason:'Playwright fixture cleanup',expectedVersion:product.version+1}});expect(cleanup.ok()).toBeTruthy();
  }
});

test('expired token returns the user to sign in',async({page})=>{
  await page.goto('/login');await page.evaluate(()=>localStorage.setItem('token','expired-token'));await page.goto('/');await expect(page).toHaveURL(/\/login$/);await expect(page.getByRole('heading',{name:'Sign in to your workspace'})).toBeVisible();
});

test('complete UI order workflow decrements inventory and rejects stale replay',async({page,request})=>{
  const login=await request.post('/api/auth/login',{data:{email:'manager@trendify.dev',password:'Trendify123!'}});const loginBody=await login.json();const headers={Authorization:`Bearer ${loginBody.accessToken}`};
  const product=await productBySku(request,headers,'SKU-COFFEE-01');
  await page.goto('/login');await page.evaluate(({token,storeId})=>{localStorage.setItem('token',token);localStorage.setItem('storeId',storeId)},{token:loginBody.accessToken,storeId:'SEA-01'});await page.goto('/');
  await page.getByLabel('Category').selectOption('Beverages');
  const row=page.locator('tbody tr').filter({hasText:'SKU-COFFEE-01'});await row.getByRole('button',{name:'Sell one'}).click();await expect(page.getByText(/Order .* confirmed/)).toBeVisible();await expect(row).toContainText(String(product.stockLevel-1));
  const stale=await request.post('/api/orders?storeId=SEA-01',{headers,data:{lines:[{productId:product.id,quantity:1,expectedVersion:product.version}]}});expect(stale.status()).toBe(400);
  const cleanup=await request.post(`/api/inventory/${product.id}/movements?storeId=SEA-01`,{headers,data:{type:'ADJUSTMENT',quantity:1,reason:'Playwright order cleanup',expectedVersion:product.version+1}});expect(cleanup.ok()).toBeTruthy();
});
