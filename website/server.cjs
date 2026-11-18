const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const base = __dirname;
const upstreamUrl = new URL(process.env.RIFT_ROOM_API || 'https://47.106.192.155/rift-timer/api');
const port = Number(process.env.PORT || 4317);
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.json': 'application/json; charset=utf-8' };
const server = http.createServer((req, res) => {
  if (req.url === '/rift-timer/api' && req.method === 'POST') {
    if (req.headers.host !== '127.0.0.1:' + port && req.headers.host !== 'localhost:' + port) {
      res.writeHead(403).end(); return;
    }
    if (req.headers['content-type'] !== 'application/json' || Number(req.headers['content-length']) > 8192) {
      res.writeHead(400).end(); return;
    }
    const remote = (upstreamUrl.protocol==='https:'?require('node:https'):http).request(upstreamUrl, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': req.headers['content-length'] || 0 }, timeout: 8000
    }, upstream => {
      res.writeHead(upstream.statusCode, { 'Content-Type':'application/json', 'Cache-Control':'no-store' });
      upstream.pipe(res);
    });
    remote.on('timeout', () => remote.destroy());
    remote.on('error', () => res.writeHead(503).end(JSON.stringify({error:'无法连接对局服务器'})));
    req.pipe(remote); return;
  }
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); } catch { res.writeHead(400).end('Bad request'); return; }
  const file = path.resolve(base, '.' + (pathname === '/' ? '/index.html' : pathname));
  if (!file.startsWith(base + path.sep)) { res.writeHead(403).end('Forbidden'); return; }
  fs.stat(file, (error, stat) => {
    if (error || !stat.isFile()) { res.writeHead(404).end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
    if (req.method === 'HEAD') { res.end(); return; }
    const stream = fs.createReadStream(file); stream.on('error', () => res.destroy()); stream.pipe(res);
  });
});
server.on('error', error => { console.error(error.code === 'EADDRINUSE' ? `端口 ${port} 已占用。请设置 PORT 环境变量后重试。` : error.message); process.exit(1); });
server.listen(port, '127.0.0.1', () => {
  const url = `http://127.0.0.1:${port}`;
  console.log(`RIFT TIMER ready: ${url}`);
  if (process.argv.includes('--open') && process.platform === 'win32') {
    require('node:child_process').execFile('cmd.exe', ['/d', '/c', 'start', '', url], { windowsHide: true }, error => {
      if (error) console.log(`请在浏览器打开 ${url}`);
    });
  }
});
