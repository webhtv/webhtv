'use strict';
const http = require('http');
let account;
let initialized = 0;
module.exports.start = async function (options) {
    account = options.account;
    initialized++;
    const server = http.createServer((req, res) => {
        const url = new URL(req.url, 'http://127.0.0.1');
        if (url.pathname === '/config') {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({video: {sites: [{key: 'origin_fixture', name: 'origin-' + account,
                type: 4, api: '/spider', header: options.headers, searchable: 1, quickSearch: 1}]}}));
            return;
        }
        if (url.pathname === '/identity') {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({account, initialized, pid: process.pid, proxyPort: globalThis.catDartServerPort(), root: process.env.NODE_PATH}));
            return;
        }
        if (url.pathname === '/spider') {
            const play = url.searchParams.get('play');
            const search = url.searchParams.get('wd');
            const ids = url.searchParams.get('ids');
            const path = play !== null ? '/player?episode=' + encodeURIComponent(play)
                : search !== null ? '/search?wd=' + encodeURIComponent(search) : '/detail?id=' + encodeURIComponent(ids || 'origin-vod');
            const request = http.get(options.base + path + '&account=' + account + '&runtime=node',
                {headers: options.headers}, response => {
                    res.writeHead(response.statusCode, response.headers);
                    response.pipe(res);
                });
            request.on('error', error => { res.statusCode = 500; res.end(String(error)); });
            return;
        }
        res.statusCode = 404;
        res.end();
    });
    await new Promise((resolve, reject) => {
        server.on('error', reject);
        server.listen(Number(process.env.DEV_HTTP_PORT || 0), '127.0.0.1', resolve);
    });
    return server;
};
