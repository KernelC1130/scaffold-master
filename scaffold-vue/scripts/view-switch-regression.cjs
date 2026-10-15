// Run against a dev/preview server with Playwright available via node_modules or NODE_PATH.
// Every API request is intercepted; fixtures never read or write real business data.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const baseURL = process.env.VIEW_QA_URL || 'http://127.0.0.1:4180';
const output = process.env.VIEW_QA_OUT || '.tmp-view-switch';
const user = { id: 1, username: 'preview', nickname: '测试管理员', roles: ['super_admin'], permissions: ['*'] };
const routes = [{ name: '系统管理', children: [{ name: '用户管理', path: '/system/user', component: 'system/user/index', icon: 'users' }] }];
const depts = [{ id: 1, name: '测试组织', status: 1, children: [{ id: 2, name: '研发部', status: 1 }] }];
const rows = Array.from({ length: 57 }, (_, i) => ({
  id: i + 1, username: `user${i + 1}`, nickname: `测试用户 ${i + 1}`, deptId: 2, deptName: '研发部',
  roleName: '平台成员', postName: '工程师', status: 1, phone: '', email: '', createTime: '2026-10-13 09:00', lastLogin: '',
}));

// Playwright's ordinary visible check ignores opacity, including transparent ancestors.
// Check the rendered result without depending on the implementation's readiness marker.
async function assertVisible(page, selectors) {
  await page.waitForFunction(selectors => selectors.every(selector => {
    const el = document.querySelector(selector);
    if (!el || !el.checkVisibility({ opacityProperty: true, visibilityProperty: true })) return false;
    let opacity = 1;
    for (let node = el; node; node = node.parentElement) opacity *= Number(getComputedStyle(node).opacity);
    const box = el.getBoundingClientRect();
    return opacity > 0.99 && box.width > 0 && box.height > 0;
  }), selectors, { timeout: 15000 });
}

async function assertView(page, view) {
  await assertVisible(page, view === 'board'
    ? ['.alt-view', '.board-card']
    : ['.search-form', '.split-side', '.data-row', '.pager']);
  assert.equal(await page.getByRole('radio', { name: view === 'board' ? '看板' : '列表', exact: true }).getAttribute('aria-checked'), 'true');
}

async function switchTo(page, view) {
  await page.getByRole('radio', { name: view === 'board' ? '看板' : '列表', exact: true }).click();
  await assertView(page, view);
}

async function fixtures(context, errors) {
  await context.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url());
    let data;
    if (request.method() !== 'GET') {
      errors.push(`Unexpected write: ${request.method()} ${url.pathname}`);
      return route.abort();
    }
    switch (url.pathname) {
      case '/api/auth/me': data = user; break;
      case '/api/auth/routes': data = { routes }; break;
      case '/api/system/users/options':
        data = { depts, roles: [{ id: 1, name: '平台成员' }], posts: [{ id: 1, name: '工程师' }], initPassword: '' };
        break;
      case '/api/system/users': {
        const pageNum = Number(url.searchParams.get('pageNum') || 1);
        const pageSize = Number(url.searchParams.get('pageSize') || 10);
        data = { rows: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: rows.length, pageNum, pageSize };
        break;
      }
      case '/api/system/dict/options/sys_normal_disable':
        data = [{ label: '正常', value: '1', status: 1, tone: 'ok' }, { label: '停用', value: '0', status: 1, tone: 'danger' }];
        break;
      default:
        errors.push(`Unexpected API: ${url.pathname}`);
        return route.abort();
    }
    await route.fulfill({ json: { code: 200, message: '操作成功', data } });
  });
}

async function main() {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ channel: process.env.VIEW_QA_BROWSER || 'chrome', headless: true });
  const reports = [];
  const scenarios = [
    { name: 'reduced-table', initial: 'table' },
    { name: 'reduced-board', initial: 'board' },
    { name: 'motion-table', initial: 'table', motion: 'no-preference' },
    { name: 'motion-board', initial: 'board', motion: 'no-preference' },
    { name: 'dark', initial: 'table', theme: 'dark' },
    { name: 'edge', initial: 'table', width: 1024, height: 700 },
    { name: 'mobile', initial: 'table', width: 390, height: 844 },
    { name: 'short', initial: 'board', height: 600 },
  ];
  try {
    for (const scenario of scenarios) {
      const viewport = { width: scenario.width || 1440, height: scenario.height || 900 };
      const context = await browser.newContext({ viewport, reducedMotion: scenario.motion || 'reduce', serviceWorkers: 'block' });
      const errors = [];
      await fixtures(context, errors);
      await context.addInitScript(({ user, scenario }) => {
        localStorage.setItem('scaffold.token', 'view-switch-fixture');
        localStorage.setItem('scaffold.user', JSON.stringify(user));
        localStorage.setItem('scaffold.expiresAt', String(Date.now() + 3600000));
        if (!localStorage.getItem('scaffold.view.users')) localStorage.setItem('scaffold.view.users', scenario.initial);
        if (!localStorage.getItem('scaffold.appearance')) localStorage.setItem('scaffold.appearance', JSON.stringify({ theme: scenario.theme || 'light', density: 'regular', railCollapsed: false }));
      }, { user, scenario });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      page.on('console', message => { if (['error', 'warning'].includes(message.type())) errors.push(message.text()); });
      try {
        await page.goto(`${baseURL}/system/user`);
        await assertView(page, scenario.initial);
        for (let i = 0; i < 3; i++) {
          await switchTo(page, 'board');
          if (i === 0) await page.screenshot({ path: path.join(output, `${scenario.name}-board.png`) });
          await switchTo(page, 'table');
          if (i === 0) await page.screenshot({ path: path.join(output, `${scenario.name}-table.png`) });
        }
        // Fast changes commit on successive frames, without waiting for entrance animations.
        for (let i = 0; i < 8; i++) {
          await page.getByRole('radio', { name: i % 2 ? '列表' : '看板', exact: true }).evaluate(el => el.click());
          await page.evaluate(() => new Promise(resolve => requestAnimationFrame(resolve)));
        }
        await assertView(page, 'table');
        for (let i = 0; i < 2; i++) {
          if (viewport.width <= 820 && i === 1) await page.locator('.rail-scrim').click({ position: { x: viewport.width - 8, y: 80 } });
          else await page.getByRole('button', { name: '切换导航', exact: true }).click();
          await assertVisible(page, ['.topbar .user-chip', '.rail-brand', '.nav-group']);
        }
        await assertView(page, 'table');
        await switchTo(page, 'board');
        await page.reload();
        await assertView(page, 'board');
        await switchTo(page, 'table');
        await page.reload();
        await assertView(page, 'table');
        // Also switch while the route's entrance animation is starting.
        await page.reload({ waitUntil: 'domcontentloaded' });
        await page.getByRole('radio', { name: '看板', exact: true }).waitFor({ state: 'attached' });
        await page.getByRole('radio', { name: '看板', exact: true }).evaluate(el => el.click());
        await assertView(page, 'board');
        await switchTo(page, 'table');
        assert.deepEqual(errors, []);
        reports.push({ scenario: scenario.name, viewport, result: 'passed', errors });
        console.log(`PASS ${scenario.name}`);
      } catch (error) {
        await page.screenshot({ path: path.join(output, `${scenario.name}-failure.png`) });
        console.error(await page.evaluate(() => ({
          pageClass: document.querySelector('.page')?.className,
          transparentElements: [...document.querySelectorAll('.reveal, .rail-brand, .nav-group, .topbar > *')]
            .filter(el => Number(getComputedStyle(el).opacity) < 0.99).map(el => ({ class: el.className, opacity: getComputedStyle(el).opacity })),
        })));
        throw error;
      } finally { await context.close(); }
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(reports, null, 2));
  } finally { await browser.close(); }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
