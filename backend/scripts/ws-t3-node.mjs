// T3 verification via Node 22 built-in WebSocket (browser-equivalent stack)
const base = 'http://127.0.0.1:8080';
const login = await (await fetch(base + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }),
})).json();
const token = login.data.accessToken;

const ws = new WebSocket('ws://localhost:5173/ws/notify?token=' + encodeURIComponent(token));
const done = (ok, msg) => { console.log((ok ? 'PASS' : 'FAIL') + '  T3-vite-proxy(node)  ' + msg); process.exit(ok ? 0 : 1); };
const timer = setTimeout(() => done(false, 'timeout waiting CONNECTED'), 8000);
ws.onerror = (e) => { clearTimeout(timer); done(false, 'error: ' + (e.message || 'ws error')); };
ws.onopen = () => console.log('open');
ws.onmessage = (ev) => {
  clearTimeout(timer);
  done(String(ev.data).includes('CONNECTED'), 'msg=' + ev.data);
};
