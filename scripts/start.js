const express = require('express');
const httpProxy = require('http-proxy');
const http = require('http');
const path = require('path');

const DIST_DIR = path.join(__dirname, '..', 'dist', 'apps', 'sumicare-web', 'browser');
const BACKEND_URL = (process.env.API_BASE_URL || '').replace(/\/+$/, '');
const PORT = process.env.PORT || 8080;
const PROXIED_PREFIXES = ['/api', '/ws', '/uploads'];

const proxy = httpProxy.createProxyServer({ target: BACKEND_URL, changeOrigin: true });

proxy.on('error', (err, req, res) => {
    console.error(`[start] proxy error for ${req.url}: ${err.message}`);
    if (res && typeof res.writeHead === 'function' && !res.headersSent) {
        res.writeHead(502);
        res.end('Bad gateway');
    } else if (res && typeof res.destroy === 'function') {
        res.destroy();
    }
});

function isProxied(url) {
    return PROXIED_PREFIXES.some((prefix) =>
        url === prefix || url.startsWith(`${prefix}/`) || url.startsWith(`${prefix}?`)
    );
}

const app = express();

app.use((req, res, next) => {
    if (BACKEND_URL && isProxied(req.url)) {
        proxy.web(req, res);
        return;
    }
    next();
});

app.use(express.static(DIST_DIR, { index: false }));

app.get('*', (req, res) => {
    res.sendFile(path.join(DIST_DIR, 'index.html'));
});

const server = http.createServer(app);

server.on('upgrade', (req, socket, head) => {
    if (BACKEND_URL && req.url.startsWith('/ws')) {
        proxy.ws(req, socket, head);
        return;
    }
    socket.destroy();
});

server.listen(PORT, () => {
    console.log(`[start] serving ${DIST_DIR} on port ${PORT}`);
    console.log(`[start] proxying ${PROXIED_PREFIXES.join(', ')} -> ${BACKEND_URL || '(no backend configured)'}`);
});
