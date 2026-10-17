import {render,screen,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {describe,expect,it,vi} from 'vitest';
import {App,Dashboard,Login,type DashboardData,type Product} from './App';
import {api,ApiError} from './api';

const dashboard:DashboardData={revenue:1000,unitsSold:80,averageOrderValue:25,grossMargin:580,revenueTrend:[{date:'2026-10-16',revenue:1000,units:80}],stockoutRisks:1,pendingRecommendations:2,storeId:'SEA-01'};
const product:Product={id:'p1',sku:'SKU-1',name:'Fixture Product',category:'Tests',supplier:'Fixture Supplier',price:12.5,cost:5,stockLevel:2,reorderThreshold:3,leadTimeDays:7,version:1};
const ok=(body:unknown)=>Promise.resolve(new Response(JSON.stringify(body),{status:200,headers:{'Content-Type':'application/json'}}));
const fail=(status:number)=>Promise.resolve(new Response('{}',{status,statusText:'error'}));
const mockFetch=(...responses:Promise<Response>[])=>vi.spyOn(globalThis,'fetch').mockImplementation(()=>responses.shift()!);
const renderDashboard=()=>render(<MemoryRouter><Dashboard/></MemoryRouter>);

describe('Dashboard',()=>{
  it('shows loading while the dashboard is pending',()=>{vi.spyOn(globalThis,'fetch').mockReturnValue(new Promise(()=>{}));renderDashboard();expect(screen.getByText('Loading live business data…')).toBeInTheDocument()});
  it('renders successful KPI and product data',async()=>{mockFetch(ok(dashboard),ok({items:[product]}));renderDashboard();expect(await screen.findByText('$1,000.00')).toBeInTheDocument();expect(screen.getByText('Fixture Product')).toBeInTheDocument();expect(screen.getByText('Reorder')).toBeInTheDocument()});
  it('renders an empty product table without fake rows',async()=>{mockFetch(ok(dashboard),ok({items:[]}));renderDashboard();expect(await screen.findByRole('heading',{name:'Inventory watchlist'})).toBeInTheDocument();expect(screen.queryAllByRole('row')).toHaveLength(1)});
  it('renders a dashboard error and retries',async()=>{mockFetch(fail(503),fail(503));renderDashboard();expect(await screen.findByRole('heading',{name:'Dashboard unavailable'})).toBeInTheDocument();await userEvent.click(screen.getByRole('button',{name:'Try again'}));await waitFor(()=>expect(fetch).toHaveBeenCalledTimes(2))});
  it('renders a product-specific error',async()=>{mockFetch(ok(dashboard),fail(500));renderDashboard();expect(await screen.findByText('Products could not be loaded.')).toBeInTheDocument()});
  it('applies category filters to dashboard and products',async()=>{mockFetch(ok(dashboard),ok({items:[]}),ok(dashboard),ok({items:[]}));renderDashboard();await screen.findByRole('heading',{name:'Inventory watchlist'});await userEvent.selectOptions(screen.getByLabelText('Category'),'Beverages');await waitFor(()=>expect(fetch).toHaveBeenCalledWith(expect.stringContaining('category=Beverages'),expect.anything()))});
  it('creates an order and refreshes inventory',async()=>{mockFetch(ok(dashboard),ok({items:[product]}),ok({id:'order-1'}),ok(dashboard),ok({items:[{...product,stockLevel:1,version:2}]}));renderDashboard();const row=await screen.findByRole('row',{name:/Fixture Product/});await userEvent.click(within(row).getByRole('button',{name:'Sell one'}));expect(await screen.findByText('Order order-1 confirmed')).toBeInTheDocument();await waitFor(()=>expect(fetch).toHaveBeenCalledTimes(5))});
});

describe('Login and routing',()=>{
  it('stores a successful JWT and store scope then navigates home',async()=>{mockFetch(ok({accessToken:'test-token',storeId:'SEA-01'}),new Promise(()=>{}));render(<MemoryRouter initialEntries={['/login']}><App/></MemoryRouter>);await userEvent.click(screen.getByRole('button',{name:'Sign in'}));await waitFor(()=>expect(localStorage.getItem('token')).toBe('test-token'));expect(localStorage.getItem('storeId')).toBe('SEA-01')});
  it('shows invalid credential feedback without storing a token',async()=>{mockFetch(fail(401));render(<MemoryRouter><Login/></MemoryRouter>);await userEvent.click(screen.getByRole('button',{name:'Sign in'}));expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();expect(localStorage.getItem('token')).toBeNull()});
  it('redirects anonymous users to sign in',async()=>{render(<MemoryRouter initialEntries={['/']}><App/></MemoryRouter>);expect(await screen.findByRole('heading',{name:'Sign in to your workspace'})).toBeInTheDocument()});
});

describe('API client',()=>{
  it('attaches a stored bearer token',async()=>{localStorage.setItem('token','jwt');mockFetch(ok({items:[]}));await api('/products');const headers=vi.mocked(fetch).mock.calls[0][1]?.headers as Headers;expect(headers.get('Authorization')).toBe('Bearer jwt')});
  it('clears an expired token and emits the unauthorized event',async()=>{localStorage.setItem('token','expired');mockFetch(fail(401));const listener=vi.fn();window.addEventListener('trendify:unauthorized',listener);await expect(api('/products')).rejects.toBeInstanceOf(ApiError);expect(localStorage.getItem('token')).toBeNull();expect(listener).toHaveBeenCalled();window.removeEventListener('trendify:unauthorized',listener)});
});
