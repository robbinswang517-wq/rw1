import { framePoint } from './geometry.js';
const $ = id => document.getElementById(id);
let token = null, generation = 0, objectUrl = null, timer = null;
let activePointer = null, lastPoint = null, touchQueue = [], sending = false;
let frameController = null;
function deadline(ms) {
  const controller = new AbortController();
  setTimeout(() => controller.abort(), ms);
  return controller.signal;
}
const status = text => { $('status').textContent = text; };
async function request(path, body, signal) {
  return fetch(path, {
    method: body === undefined ? 'GET' : 'POST', cache: 'no-store', signal,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
}
function reset() {
  generation++; token = null; clearTimeout(timer); frameController?.abort();
  activePointer = null; lastPoint = null; touchQueue = [];
  $('pairing').hidden = false; $('stage').hidden = true; $('disconnect').hidden = true;
  $('screen').removeAttribute('src');
  if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
}
async function poll(id) {
  if (!token || generation !== id) return;
  const controller = new AbortController(); frameController = controller;
  const timeout = setTimeout(() => controller.abort(), 3000);
  try {
    const response = await request('/api/frame', undefined, controller.signal);
    if (id !== generation) return;
    if (response.status === 401) { reset(); status('配对已失效，请重新连接'); return; }
    if (response.ok) {
      const blob = await response.blob();
      if (id !== generation) return;
      const nextUrl = URL.createObjectURL(blob);
      const image = new Image(); image.src = nextUrl;
      try { await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = reject; }); }
      catch (error) { URL.revokeObjectURL(nextUrl); throw error; }
      if (id !== generation) { URL.revokeObjectURL(nextUrl); return; }
      $('screen').src = nextUrl;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = nextUrl; $('waiting').hidden = true; status('已连接 · JPEG ≤10 fps');
    } else { $('waiting').hidden = false; status('等待接收端 Android Auto 投影'); }
  } catch (_) { if (id === generation) { $('waiting').hidden = false; status('连接中断，正在重试…'); } }
  finally {
    clearTimeout(timeout);
    if (token && generation === id) timer = setTimeout(() => poll(id), 100);
  }
}
$('pair-form').addEventListener('submit', async event => {
  event.preventDefault(); $('connect').disabled = true; $('error').textContent = '';
  try {
    const response = await request('/api/pair', { code: $('code').value.trim() }, deadline(5000));
    if (!response.ok) throw new Error('配对失败：请核对配对码、断开其他浏览器，或等待 30 秒再试。');
    token = (await response.json()).token; const id = ++generation;
    $('pairing').hidden = true; $('stage').hidden = false; $('disconnect').hidden = false;
    $('waiting').hidden = false; poll(id);
  } catch (error) { $('error').textContent = error.message || '无法连接接收端'; }
  finally { $('connect').disabled = false; }
});
async function flushTouch() {
  if (sending || !token) return;
  sending = true; const id = generation;
  try {
    while (touchQueue.length && token && id === generation) {
      const event = touchQueue.shift();
      const response = await request('/api/touch', event, deadline(1500));
      if (id !== generation) break;
      if (!response.ok) { touchQueue = []; activePointer = null; break; }
    }
  } catch (_) { if (id === generation) { touchQueue = []; activePointer = null; } }
  finally { sending = false; if (touchQueue.length && token) flushTouch(); }
}
function queueTouch(action, point) {
  const event = { action, ...point };
  if (action === 'move' && touchQueue[touchQueue.length - 1]?.action === 'move') touchQueue[touchQueue.length - 1] = event;
  else touchQueue.push(event);
  flushTouch();
}
const screen = $('screen');
function point(event, clamp) { return framePoint(screen.getBoundingClientRect(), screen.naturalWidth, screen.naturalHeight, event.clientX, event.clientY, clamp); }
screen.addEventListener('pointerdown', event => {
  if (activePointer !== null || !token || !$('waiting').hidden || (event.pointerType === 'mouse' && event.button !== 0)) return;
  const p = point(event, false); if (!p) return;
  event.preventDefault(); activePointer = event.pointerId; lastPoint = p;
  screen.setPointerCapture(event.pointerId); queueTouch('down', p);
});
screen.addEventListener('pointermove', event => {
  if (activePointer !== event.pointerId) return;
  const p = point(event, true); if (!p) return;
  event.preventDefault(); lastPoint = p; queueTouch('move', p);
});
function endPointer(event, action) {
  if (activePointer !== event.pointerId) return;
  queueTouch(action, point(event, true) || lastPoint); activePointer = null;
}
screen.addEventListener('pointerup', event => endPointer(event, 'up'));
screen.addEventListener('pointercancel', event => endPointer(event, 'cancel'));
screen.addEventListener('lostpointercapture', event => endPointer(event, 'cancel'));
setInterval(() => { if (activePointer !== null && lastPoint) queueTouch('move', lastPoint); }, 500);
document.addEventListener('visibilitychange', () => {
  if (document.hidden && activePointer !== null) { queueTouch('cancel', lastPoint); activePointer = null; }
});
$('disconnect').addEventListener('click', async () => {
  try { await request('/api/disconnect', {}, deadline(2000)); } catch (_) {}
  reset(); status('已断开 · Disconnected');
});
$('fullscreen').addEventListener('click', async () => {
  try { if (document.fullscreenElement) await document.exitFullscreen(); else await document.body.requestFullscreen(); }
  catch (_) { status('此浏览器不支持全屏，请使用车机浏览器全屏选项'); }
});
// Tokens stay in memory. Browser disappearance releases the touch after 1.5s and ownership after 15s.
window.addEventListener('pagehide', () => { token = null; generation++; frameController?.abort(); });
