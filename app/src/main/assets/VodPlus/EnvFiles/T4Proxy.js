#!/usr/bin/env node
// ============================================================================
// WebHTV 实验室 · T4 代理服务器（Node 版，参考实现）
// ============================================================================
//
// 用途：把壳子当前加载的所有站源（jar / js / py / php 以及 t1 / t4 远端接口）
// 统一暴露成苹果CMS V10（T4）接口，供 T4 客户端直接订阅。
//
// 实现方式：调用 App 内置 /vod/api（默认 http://127.0.0.1:9978/vod/api）。
// 转换只在 App 端维护，Node 入口不再重复走旧 /spider 的 T3-only/无分页路径。
// Node 运行时仅用于保留实验室入口；不安装 Node 也能直接使用 App 内置网关。
//
// 与 App 内置 /vod/api 的关系：
//   - App 内置 /vod/api（见 app/src/main/java/.../server/process/VodApi.java）
//     在同一进程里直接调用爬虫，不需要安装任何实验室包，是首选方式；
//   - 本脚本是不装 Node 也能读的参考实现，也是「实验室 → Node.js → T4代理服务器」
//     命令实际执行的脚本，端口默认 10998。
//
// 用法：
//   node T4Proxy.js [--port 10998] [--host http://127.0.0.1:9978] [--key 站点key]
//
// 接口：
//   配置接口: /?ac=config
//   首页接口: /站源key
//   分类接口: /站源key?t=分类ID&pg=页码
//   详情接口: /站源key?ids=视频ID
//   搜索接口: /站源key?wd=关键词&pg=页码
//   播放接口: /站源key?flag=播放源&id=播放ID
//   筛选参数: area=地区&year=年份&type=类型&class=剧情&lang=语言
//
// 提示：ac 参数可省略，系统会根据其他参数自动识别接口类型。
// ============================================================================

const http = require('http');
const https = require('https');
const { URL } = require('url');
const os = require('os');

// ============================================================================
// 配置常量
// ============================================================================
const DEFAULT_SPIDER_API_HOST = 'http://127.0.0.1:9978';
const DEFAULT_PORT = 10998;
const REQUEST_TIMEOUT = 30000; // 毫秒
const FILTER_FIELDS = ['area', 'year', 'type', 'class', 'lang'];

// ============================================================================
// Spider API 客户端
// ============================================================================
class SpiderApiClient {
    constructor(host, timeout = REQUEST_TIMEOUT) {
        this.host = host.replace(/\/$/, '');
        this.timeout = timeout;
    }

    async configAction() {
        const data = await this.post('/vod/api', { ac: 'config' });
        return Array.isArray(data.sites) ? { code: 0, data } : data;
    }

    async execute(key, method, params = {}) {
        const input = { key, ...params };
        if (method === 'categoryContent') input.t = params.tid;
        if (method === 'playerContent') input.play = params.id;
        const data = await this.post('/vod/api', input);
        return data.code === -1 ? data : { code: 0, data };
    }

    homeContent(key, filter = true) {
        return this.execute(key, 'homeContent', { filter });
    }

    categoryContent(key, tid, page = 1, filter = true, extend = {}) {
        return this.execute(key, 'categoryContent', { tid, pg: page, filter, extend });
    }

    detailContent(key, ids) {
        return this.execute(key, 'detailContent', { ids: Array.isArray(ids) ? ids : [ids] });
    }

    searchContent(key, keyword, page = 1, quick = false) {
        return this.execute(key, 'searchContent', { wd: keyword, pg: page, quick });
    }

    playerContent(key, flag, id, flags = []) {
        return this.execute(key, 'playerContent', { flag, id, flags });
    }

    post(endpoint, data) {
        return new Promise((resolve) => {
            let parsedUrl;
            try {
                parsedUrl = new URL(this.host + endpoint);
            } catch {
                resolve({ code: -1, msg: `无效的 Spider API 地址: ${this.host}` });
                return;
            }
            if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
                resolve({ code: -1, msg: '仅支持 HTTP/HTTPS 网关地址' });
                return;
            }
            const isHttps = parsedUrl.protocol === 'https:';
            const client = isHttps ? https : http;
            const postData = JSON.stringify(data);
            const options = {
                hostname: parsedUrl.hostname,
                port: parsedUrl.port || (isHttps ? 443 : 80),
                path: parsedUrl.pathname + parsedUrl.search,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json; charset=utf-8',
                    'Content-Length': Buffer.byteLength(postData)
                },
                timeout: this.timeout
            };

            const req = client.request(options, (res) => {
                let body = '';
                res.setEncoding('utf8');
                let bytes = 0;
                res.on('data', (chunk) => {
                    bytes += Buffer.byteLength(chunk);
                    if (bytes > 16 * 1024 * 1024) req.destroy(new Error('Response too large'));
                    else body += chunk;
                });
                res.on('error', () => resolve({ code: -1, msg: 'Response interrupted' }));
                res.on('aborted', () => resolve({ code: -1, msg: 'Response interrupted' }));
                res.on('end', () => {
                    if (res.statusCode < 200 || res.statusCode >= 300) {
                        resolve({ code: -1, msg: `Gateway HTTP ${res.statusCode}` });
                        return;
                    }
                    try {
                        const data = JSON.parse(body);
                        resolve(data && typeof data === 'object' && !Array.isArray(data) ? data : { code: -1, msg: 'Invalid JSON object' });
                    } catch {
                        resolve({ code: -1, msg: 'Invalid JSON response' });
                    }
                });
            });

            req.on('error', (e) => resolve({ code: -1, msg: `Request failed: ${e.message}` }));
            req.on('timeout', () => {
                req.destroy();
                resolve({ code: -1, msg: 'Request timeout' });
            });

            req.write(postData);
            req.end();
        });
    }
}

// ============================================================================
// Type 4 代理
// ============================================================================
class SpiderApiProxy {
    constructor(client, defaultKey = '') {
        this.client = client;
        this.defaultKey = defaultKey;
    }

    async handle(req, res) {
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, HEAD, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

        if (req.method === 'OPTIONS') {
            res.statusCode = 204;
            res.end();
            return;
        }

        if (!['GET', 'POST', 'HEAD'].includes(req.method)) {
            res.statusCode = 405;
            res.end(JSON.stringify({ code: -1, msg: 'Unsupported HTTP method' }));
            return;
        }
        try {
            res.end(JSON.stringify(this.externalize(await this.dispatch(req), req), null, 2));
        } catch (e) {
            res.end(JSON.stringify({ code: -1, msg: e.message }));
        }
    }

    async dispatch(req) {
        const input = await this.getInput(req);

        let key = this.getKeyFromPath(req);
        if (!key) key = input.key || this.defaultKey;

        // 兼容处理：play 参数作为 id 的别名
        if (input.play && !input.id) input.id = input.play;

        const ac = this.route(input);
        if (ac === 'config' || (!key && Object.keys(input).length === 0)) return await this.getProxiedConfig(req);

        if (!key) return { code: -1, msg: '缺少 key 参数' };

        const pg = Math.max(1, parseInt(input.pg, 10) || 1);
        let apiResult = null;

        switch (ac) {
            case 'search':
                apiResult = await this.client.searchContent(key, input.wd, pg, ['1', 'true', 'yes'].includes(String(input.quick).toLowerCase()));
                break;

            case 'detail': {
                const ids = Array.isArray(input.ids) ? input.ids : String(input.ids || '').split(',').filter(Boolean);
                if (!ids.length) return { code: -1, msg: '缺少 ids 参数' };
                apiResult = await this.client.detailContent(key, ids);
                break;
            }

            case 'play': {
                if (!input.flag || !input.id) return { code: -1, msg: '缺少 flag 或 id 参数' };
                const flags = input.flags ? String(input.flags).split(',') : [];
                apiResult = await this.client.playerContent(key, input.flag, input.id, flags);
                // 播放接口直接返回原始数据，不做苹果CMS 信封包装
                if (apiResult && apiResult.code === 0) return apiResult.data || apiResult;
                return { code: -1, msg: (apiResult && apiResult.msg) || '请求失败' };
            }

            case 'list':
            default: {
                const t = input.t || '';
                const filterValue = input.filter === undefined ? input.f : input.filter;
                const filter = filterValue === undefined || ['1', 'true', 'yes'].includes(String(filterValue).toLowerCase());
                if (!t) {
                    apiResult = await this.client.homeContent(key, filter);
                } else {
                    const extend = Object.create(null);
                    const merge = (value) => {
                        if (value && typeof value === 'object' && !Array.isArray(value)) Object.assign(extend, value);
                    };
                    try { if (input.ext) merge(JSON.parse(Buffer.from(String(input.ext), 'base64').toString('utf8'))); } catch { }
                    try { merge(typeof input.extend === 'string' ? JSON.parse(input.extend) : input.extend); } catch { }
                    FILTER_FIELDS.forEach((field) => {
                        if (input[field]) extend[field] = input[field];
                    });
                    apiResult = await this.client.categoryContent(key, t, pg, filter, extend);
                }
                break;
            }
        }

        if (apiResult && apiResult.code === 0) return this.formatMacCmsResponse(apiResult.data, pg);
        return { code: -1, msg: (apiResult && apiResult.msg) || '请求失败' };
    }

    /** ac=detail 也用于分类；保持与 App T4 客户端一致。 */
    route(input) {
        if (input.ac === 'config' || input.ac === 'site') return 'config';
        if (input.wd) return 'search';
        if (input.ids !== undefined) return 'detail';
        if (input.ac === 'play' || input.play || (input.flag && input.id)) return 'play';
        return 'list';
    }

    getKeyFromPath(req) {
        const pathParts = req.url.split('?')[0].split('/').filter((p) => p);
        if (pathParts.length === 0) return '';
        try {
            return decodeURIComponent(pathParts[0]);
        } catch {
            return pathParts[0];
        }
    }

    formatMacCmsResponse(data, currentPage = 1) {
        const list = Array.isArray(data && data.list) ? data.list : [];
        const listCount = list.length;
        const page = data && data.page ? parseInt(data.page) : currentPage;
        const limit = data && data.limit ? parseInt(data.limit) : (listCount > 0 ? listCount : 20);
        // 与 App 内置 /vod/api 一致：站源没给总页数时按 9999 处理
        const pagecount = data && data.pagecount ? parseInt(data.pagecount) : (listCount ? 9999 : page);
        const total = data && data.total !== undefined ? parseInt(data.total) : pagecount * limit;

        const response = { page, pagecount, limit, total, list };
        if (data && data.class) response.class = data.class;
        if (data && data.filters) response.filters = data.filters;
        return response;
    }

    async getProxiedConfig(req) {
        const status = await this.client.configAction('status');
        if (status.code !== 0 || !status.data || !status.data.sites) {
            return { code: -1, msg: '获取配置失败：请运行含 /vod/api 的 WebHTV，并加载有效点播源' };
        }

        const baseUrl = this.getBaseUrl(req);
        const sites = status.data.sites.map((site) => ({
            key: site.key,
            name: site.name,
            api: `${baseUrl}/?key=${encodeURIComponent(site.key)}`,
            type: '4',
            searchable: site.searchable ?? 1,
            quickSearch: site.quickSearch ?? 1,
            filterable: site.filterable ?? 1
        }));

        return {
            spider: '',
            wallpaper: '',
            warningText: '资源来自网络，仅供学习使用',
            sites,
            doh: status.data.doh || [],
            rules: status.data.rules || [],
            lives: []
        };
    }

    getBaseUrl(req) {
        const protocol = req.connection && req.connection.encrypted ? 'https' : 'http';
        return `${protocol}://${req.headers.host || 'localhost'}`;
    }

    // Node 和 App 是两个端口。客户端仍需能访问 App 的媒体代理端口。
    externalize(data, req) {
        let upstream;
        let publicHost;
        try {
            upstream = new URL(this.client.host);
            publicHost = new URL(this.getBaseUrl(req)).hostname;
        } catch {
            throw new Error('无效的网关或 Host 地址');
        }
        if (!['127.0.0.1', 'localhost', '[::1]'].includes(upstream.hostname)) return data;
        const rewrite = (value) => {
            if (typeof value === 'string') {
                try {
                    const url = new URL(value);
                    if (['http:', 'https:'].includes(url.protocol)
                        && ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
                        && (url.port || '80') === (upstream.port || '80')) {
                        url.hostname = publicHost;
                        return url.toString();
                    }
                } catch { }
                return value;
            }
            if (Array.isArray(value)) return value.map(rewrite);
            if (value && typeof value === 'object') {
                for (const field of ['url', 'v', 'values', 'server']) if (value[field] !== undefined) value[field] = rewrite(value[field]);
            }
            return value;
        };
        for (const field of ['url', 'urls', 'playUrl', 'subs', 'drm']) if (data[field] !== undefined) data[field] = rewrite(data[field]);
        for (const vod of data.list || []) if (vod && vod.vod_pic) vod.vod_pic = rewrite(vod.vod_pic);
        return data;
    }

    async getInput(req) {
        const input = Object.create(null);
        const queryIndex = req.url.indexOf('?');
        if (queryIndex !== -1) {
            for (const [key, value] of new URLSearchParams(req.url.slice(queryIndex + 1))) {
                input[key] = value;
            }
        }
        if (req.method === 'POST') {
            const body = await this.readBody(req);
            let parsed;
            try { parsed = JSON.parse(body); } catch { throw new Error('POST body 必须是有效 JSON'); }
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('POST body 必须是 JSON 对象');
            Object.assign(input, parsed);
        }
        return input;
    }

    readBody(req) {
        return new Promise((resolve, reject) => {
            let body = '';
            let bytes = 0;
            req.on('data', (chunk) => {
                bytes += chunk.length;
                if (bytes > 1024 * 1024) reject(new Error('Request too large'));
                else body += chunk;
            });
            req.on('end', () => resolve(body));
            req.on('error', reject);
            req.on('aborted', () => reject(new Error('Request interrupted')));
        });
    }
}

// ============================================================================
// 主入口
// ============================================================================
function parseArgs(argv) {
    const args = { port: DEFAULT_PORT, host: DEFAULT_SPIDER_API_HOST, key: '' };
    for (let i = 0; i < argv.length; i++) {
        const value = argv[i + 1];
        if ((argv[i] === '--port' || argv[i] === '-p') && value) args.port = parseInt(value, 10);
        else if ((argv[i] === '--host' || argv[i] === '-H') && value) args.host = value;
        else if ((argv[i] === '--key' || argv[i] === '-k') && value) args.key = value;
    }
    return args;
}

function lanAddresses() {
    const result = [];
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const net of interfaces[name]) {
            if (net.family === 'IPv4' && !net.internal) result.push(net.address);
        }
    }
    return result;
}

function main() {
    const args = parseArgs(process.argv.slice(2));
    const client = new SpiderApiClient(args.host, REQUEST_TIMEOUT);
    const proxy = new SpiderApiProxy(client, args.key);
    const server = http.createServer((req, res) => proxy.handle(req, res));

    server.listen(args.port, '0.0.0.0', () => {
        console.log('Spider API Type 4 代理服务器已启动');
        console.log(`监听地址: http://0.0.0.0:${args.port}`);
        lanAddresses().forEach((ip) => console.log(`  http://${ip}:${args.port}`));
        console.log(`Spider API: ${args.host}`);
        console.log('');
        console.log('使用示例:');
        console.log(`  配置接口: http://localhost:${args.port}?ac=config`);
        console.log(`  首页接口: http://localhost:${args.port}/站源key`);
        console.log(`  分类接口: http://localhost:${args.port}/站源key?t=分类ID&pg=页码`);
        console.log(`  详情接口: http://localhost:${args.port}/站源key?ids=视频ID`);
        console.log(`  搜索接口: http://localhost:${args.port}/站源key?wd=关键词&pg=页码`);
        console.log(`  播放接口: http://localhost:${args.port}/站源key?flag=播放源&id=播放ID`);
        console.log('');
        console.log('提示: ac参数可省略，系统会根据其他参数自动识别接口类型');
        console.log('筛选参数: area=地区&year=年份&type=类型&class=剧情&lang=语言');
        console.log('App 内置同能力接口（无需安装本脚本）: /vod/api');
    });

    server.on('error', (e) => {
        if (e.code === 'EADDRINUSE') {
            console.error(`错误: 端口 ${args.port} 已被占用,请强制结束app或更换端口后重新运行`);
        } else {
            console.error('服务器错误:', e.message);
        }
        process.exit(1);
    });
}

if (require.main === module) main();

module.exports = { SpiderApiClient, SpiderApiProxy, parseArgs };
