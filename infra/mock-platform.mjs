// Contract simulator, not an implementation of Zuora. State is intentionally ephemeral.
import { createServer } from 'node:http';

const accounts = new Map();
const receipts = new Map();
let outage = false;
function send(response, status, body) { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(body)); }
const server = createServer(async (request, response) => {
  try {
    if (request.url === '/health') return send(response, 200, { status: 'UP', available: !outage });
    if (request.url === '/accounts' && request.method === 'GET') return send(response, 200, [...accounts.values()]);
    if (request.url === '/admin/outage' && request.method === 'PUT') {
      let text = ''; for await (const chunk of request) text += chunk;
      outage = Boolean(JSON.parse(text).enabled); return send(response, 200, { outage });
    }
    if (request.method !== 'PUT' || !/^\/accounts\/[a-f0-9-]{36}$/.test(request.url)) return send(response, 404, { error: 'Not found' });
    if (outage) return send(response, 503, { error: 'Simulated platform outage' });
    const key = request.headers['idempotency-key'];
    if (!key) return send(response, 400, { error: 'Idempotency-Key is required' });
    let text = '';
    for await (const chunk of request) { text += chunk; if (text.length > 16384) return send(response, 413, { error: 'Body too large' }); }
    const account = JSON.parse(text);
    if (receipts.has(key)) return send(response, receipts.get(key) === text ? 200 : 409, { duplicate: true });
    if (request.url !== `/accounts/${account.id}` || !Number.isSafeInteger(account.version) || account.version < 0) return send(response, 400, { error: 'Invalid account snapshot' });
    const previous = accounts.get(account.id);
    if (!previous || previous.version < account.version) accounts.set(account.id, account);
    receipts.set(key, text);
    return send(response, 200, { accepted: true, version: accounts.get(account.id).version });
  } catch { send(response, 400, { error: 'Invalid JSON request' }); }
});
server.listen(8090, '0.0.0.0', () => console.log('Demo billing platform listening on :8090'));
