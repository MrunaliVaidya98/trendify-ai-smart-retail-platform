import {defineConfig, devices} from '@playwright/test';
export default defineConfig({testDir:'./e2e',fullyParallel:false,retries:0,reporter:[['list'],['html',{open:'never'}]],use:{baseURL:process.env.BASE_URL ?? 'http://127.0.0.1:4200',trace:'retain-on-failure',screenshot:'only-on-failure'},projects:[{name:'chromium',use:{...devices['Desktop Chrome']}}]});
