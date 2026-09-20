// Static assets only; no API, database or dependency on the retired frontend.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const port = Number(process.env.PORT || 8081);
const publicFiles = new Set(['index.html', 'styles.css', 'app.js', 'api.js', 'data.js', 'favicon.svg']);
const types = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  const name = new URL(req.url, 'http://localhost').pathname.slice(1) || 'index.html';
  if (!publicFiles.has(name) || !['GET', 'HEAD'].includes(req.method)) {
    res.writeHead(404); res.end('Not found'); return;
  }
  fs.readFile(path.join(__dirname, name), (error, bytes) => {
    if (error) { res.writeHead(500); res.end('Unable to load asset'); return; }
    res.writeHead(200, { 'Content-Type': types[path.extname(name)] + '; charset=utf-8', 'Cache-Control': 'no-cache' });
    res.end(req.method === 'HEAD' ? undefined : bytes);
  });
});
server.on('error', error => { console.error(error.message); process.exitCode = 1; });
server.listen(port, '127.0.0.1', () => console.log(`Frontend: http://localhost:${server.address().port}`));
