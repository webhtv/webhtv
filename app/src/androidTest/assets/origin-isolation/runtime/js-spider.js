let account = '';
let options = {};
let initialized = 0;
function fetchJson(path) {
    const response = req(options.base + path, {headers: options.headers});
    if (response.code !== 200) throw new Error('fixture HTTP ' + response.code + ' account=' + account);
    return response.content;
}
export default {
    init(ext) {
        options = typeof ext === 'string' ? JSON.parse(ext) : ext;
        account = options.account;
        initialized++;
    },
    home() { return JSON.stringify({class: [], list: [{vod_id: 'origin-vod', vod_name: account + ':js:' + initialized}]}); },
    homeVod() { return JSON.stringify({list: []}); },
    category() { return JSON.stringify({list: []}); },
    detail(id) { return fetchJson('/detail?account=' + account + '&id=' + id + '&runtime=js'); },
    search() { return fetchJson('/search?account=' + account + '&runtime=js'); },
    play(flag, id) { return fetchJson('/player?account=' + account + '&episode=' + encodeURIComponent(id) + '&runtime=js'); },
    proxy() { return [200, 'text/plain', account + ':js:' + initialized]; },
    sniffer() { return false; },
    isVideo(url) { return /m3u8|mkv|mp4/.test(url); },
    destroy() {}
};
