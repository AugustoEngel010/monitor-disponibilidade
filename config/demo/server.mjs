import http from 'node:http';
import {pathToFileURL} from 'node:url';

export async function startServer(port = 8080, delayMs = 15000) {
  const modes = {main: 'healthy', secondary: 'healthy'};
  const active = {main: 0, secondary: 0};
  const server = http.createServer(async (req, res) => {
    const send = (status, value) => {
      res.writeHead(status, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
      res.end(JSON.stringify(value));
    };
    if (req.method === 'GET' && req.url === '/control') return send(200, {...modes, delayMs, active});
    const target = /^\/targets\/(main|secondary)$/.exec(req.url);
    if (target) {
      if (req.method !== 'GET') return send(405, {error: 'method'});
      const mode = modes[target[1]];
      active[target[1]]++;
      res.on('close', () => { active[target[1]]--; });
      if (mode === 'slow') await new Promise(resolve => setTimeout(resolve, delayMs));
      return send(mode === 'error' ? 503 : 200, {target: target[1], mode});
    }
    const control = /^\/control\/([^/]+)\/([^/]+)$/.exec(req.url);
    if (!control || !Object.hasOwn(modes, control[1])) return send(404, {error: 'resource'});
    if (req.method !== 'POST') return send(405, {error: 'method'});
    if (!['healthy', 'error', 'slow'].includes(control[2])) return send(400, {error: 'mode'});
    modes[control[1]] = control[2];
    send(200, modes);
  });
  await new Promise(resolve => server.listen(port, '0.0.0.0', resolve));
  return server;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await startServer();
