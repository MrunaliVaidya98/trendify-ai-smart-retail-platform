import {expect, test} from '@playwright/test';

test('demo login renders live KPI and inventory data',async({page})=>{
  await page.goto('/login');
  await page.getByLabel('Email').fill('admin@trendify.dev');
  await page.getByLabel('Password').fill('Trendify123!');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page.getByText('NET REVENUE',{exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Inventory watchlist'})).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(3);
});

test('inventory stock-in is reflected by the dashboard and is cleaned up',async({page,request})=>{
  const login=await request.post('/api/auth/login',{data:{email:'admin@trendify.dev',password:'Trendify123!'}});expect(login.ok()).toBeTruthy();const token=(await login.json()).accessToken;const headers={Authorization:`Bearer ${token}`};
  const list=await request.get('/api/products',{headers});const products=(await list.json()).items;const product=products.find((p:{sku:string})=>p.sku==='SKU-COFFEE-01');expect(product).toBeTruthy();
  const stockIn=await request.post(`/api/inventory/${product.id}/movements`,{headers,data:{type:'STOCK_IN',quantity:1,reason:'Playwright isolated fixture',expectedVersion:product.version}});expect(stockIn.ok()).toBeTruthy();
  try{
    await page.goto('/login');
    await page.evaluate((value)=>localStorage.setItem('token',value),token);
    await page.goto('/');
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
  const list=await request.get('/api/products?storeId=SEA-01',{headers});const product=(await list.json()).items.find((p:{sku:string})=>p.sku==='SKU-COFFEE-01');expect(product).toBeTruthy();
  await page.goto('/login');await page.evaluate(({token,storeId})=>{localStorage.setItem('token',token);localStorage.setItem('storeId',storeId)},{token:loginBody.accessToken,storeId:'SEA-01'});await page.goto('/');
  const row=page.locator('tbody tr').filter({hasText:'SKU-COFFEE-01'});await row.getByRole('button',{name:'Sell one'}).click();await expect(page.getByText(/Order .* confirmed/)).toBeVisible();await expect(row).toContainText(String(product.stockLevel-1));
  const stale=await request.post('/api/orders?storeId=SEA-01',{headers,data:{lines:[{productId:product.id,quantity:1,expectedVersion:product.version}]}});expect(stale.status()).toBe(400);
  const cleanup=await request.post(`/api/inventory/${product.id}/movements?storeId=SEA-01`,{headers,data:{type:'ADJUSTMENT',quantity:1,reason:'Playwright order cleanup',expectedVersion:product.version+1}});expect(cleanup.ok()).toBeTruthy();
});
