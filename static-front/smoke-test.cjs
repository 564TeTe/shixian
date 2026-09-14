/* Offline auth-gate smoke test. Requires Node.js 22+ and Chrome/Edge. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

const candidates = [
  process.env.PROTOTYPE_BROWSER,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Users/zhaom/AppData/Local/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
].filter(Boolean);
const browserPath = candidates.find(candidate => fs.existsSync(candidate));
if (!browserPath) throw new Error('请通过 PROTOTYPE_BROWSER 指定 Chrome 或 Edge 可执行文件。');

const output = fs.mkdtempSync(path.join(os.tmpdir(), 'teaching-auth-'));
const profile = path.join(output, 'browser-profile');
const browser = spawn(
  browserPath,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-debugging-port=0',
    `--user-data-dir=${profile}`,
    'about:blank'
  ],
  { windowsHide: true, stdio: 'ignore' }
);
let socket;
let count = 0;
const pending = new Map();
const errors = [];
const network = [];
const check = (value, message) => {
  assert.ok(value, message);
  count += 1;
  console.log(`PASS ${message}`);
};

async function run() {
  const portFile = path.join(profile, 'DevToolsActivePort');
  for (let index = 0; index < 100 && !fs.existsSync(portFile); index += 1) await delay(100);
  assert.ok(fs.existsSync(portFile), '浏览器未启动');

  const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });

  let sequence = 0;
  socket.addEventListener('message', event => {
    const data = JSON.parse(String(event.data));
    if (data.id && pending.has(data.id)) {
      const item = pending.get(data.id);
      pending.delete(data.id);
      clearTimeout(item.timer);
      if (data.error) item.reject(new Error(JSON.stringify(data.error)));
      else item.resolve(data.result);
    }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.text);
    if (data.method === 'Network.requestWillBeSent' && /^https?:/.test(data.params.request.url)) {
      network.push(data.params.request.url);
    }
  });

  function send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++sequence;
      const timer = setTimeout(() => {
        pending.delete(id);
        reject(new Error(`CDP timeout: ${method}`));
      }, 10000);
      pending.set(id, { resolve, reject, timer });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }

  async function evaluate(expression) {
    const result = await send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true
    });
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  }

  const exists = selector => evaluate(`!!document.querySelector(${JSON.stringify(selector)})`);
  const text = () => evaluate('document.body.innerText');

  await send('Runtime.enable');
  await send('Network.enable');
  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false
  });
  await send('Page.navigate', { url: pathToFileURL(path.join(__dirname, 'index.html')).href });

  for (let index = 0; index < 60 && !(await exists('[data-form="login"]')); index += 1) {
    await delay(100);
  }
  check(await exists('[data-form="login"]'), '未登录时默认显示登录界面');
  check((await text()).includes('登录教学工作台'), '登录标题可见');
  check((await text()).includes('使用后端数据库中的账号登录'), '登录页明确使用数据库账号');
  check(await exists('[name="role"]'), '登录身份选择器存在且包含管理员／教师');
  check(await exists('[name="username"]'), '用户名输入框存在');
  check(await exists('[name="password"]'), '密码输入框存在');
  check(await exists('[data-form="login"] button[type="submit"]'), '登录按钮存在');
  check((await evaluate('document.title')) === '登录 · 实验教学项目管理系统', '登录页标题正确');

  await evaluate('location.hash = "tasks"');
  await delay(160);
  check(await exists('[data-form="login"]'), '未登录访问业务页仍被拦截到登录界面');
  check((await evaluate('location.hash')) === '#login', '业务页地址被重定向为 #login');

  await send('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true
  });
  await send('Page.reload');
  for (let index = 0; index < 30 && !(await exists('[data-form="login"]')); index += 1) {
    await delay(100);
  }
  check(
    await evaluate('document.documentElement.scrollWidth <= window.innerWidth'),
    '登录页手机宽度无横向溢出'
  );
  check(errors.length === 0, `浏览器脚本异常数为 0：${errors.join(', ')}`);
  check(network.length === 0, `未登录验证不依赖外部 HTTP 请求：${network.join(', ')}`);

  console.log(`\n${count} checks passed.`);
}

run()
  .catch(error => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => {
    if (socket) socket.close();
    for (const item of pending.values()) clearTimeout(item.timer);
    browser.kill();
  });
