/*
 * 豆瓣短评示例插件（webhtv-plugin，sdk 1）。
 *
 * 它演示三件事凑在一起能做什么：panel 槽位渲染（脚本返回 HTML，宿主用侧滑 WebView 打开）、
 * plugin.data.episode 上下文（宿主把当前剧集信息塞进 action payload 的 context 字段）、
 * net.request 联网（脚本自己发 HTTP，宿主不经手业务逻辑）。
 *
 * 安全边界要说清楚：宿主**不提供域名白名单**。插件与站源、字幕源同级信任，凭 net.request
 * 权限就能直接联网，因此本文件发出的每一次请求都会进入宿主的审计日志（只记 host + path，
 * query 与 fragment 被丢弃，避免把参数里的签名写进日志）。想拦住某个插件的网络行为，
 * 靠的是它声明了哪些权限，不是靠 URL 过滤。
 *
 * 兼容性：这份脚本按 ES5 写（var / function / 字符串拼接），因为 QuickJS 版本与宿主
 * 转译策略都不在插件作者的控制范围内。改这个文件时不要引入箭头函数、let/const、
 * 模板字符串或 async/await。
 */

var UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
var SUGGEST_URL = 'https://movie.douban.com/j/subject_suggest?q=';
var INTERESTS_URL = 'https://m.douban.com/rexxar/api/v2/movie/';
var DEFAULT_COUNT = 20;
var MAX_COUNT = 50;

var STYLE = ''
  + 'html,body{margin:0;padding:0;background:#f5f5f7;color:#1c1c1e;'
  + 'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Helvetica Neue",Arial,"PingFang SC","Microsoft YaHei",sans-serif;}'
  + 'body{padding:12px 12px 24px;}'
  + 'h1{font-size:16px;line-height:1.4;margin:0 0 12px;font-weight:600;}'
  + 'ul.cards{list-style:none;margin:0;padding:0;}'
  + '.card{background:#fff;border-radius:12px;padding:12px;margin:0 0 10px;box-shadow:0 1px 2px rgba(0,0,0,.06);}'
  + '.head{font-size:12px;color:#8e8e93;margin-bottom:6px;}'
  + '.stars{color:#f5a623;font-size:13px;letter-spacing:1px;}'
  + '.text{font-size:14px;line-height:1.65;margin:6px 0 0;white-space:pre-wrap;word-break:break-word;}'
  + '.note{font-size:14px;line-height:1.7;color:#3a3a3c;background:#fff;border-radius:12px;padding:14px;}';

/** 评论正文来自第三方，必须转义，否则别人写一段 <script> 就能在宿主面板里执行。 */
function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function page(title, body) {
  return '<!DOCTYPE html><html><head><meta charset="utf-8">'
    + '<meta name="viewport" content="width=device-width, initial-scale=1">'
    + '<style>' + STYLE + '</style></head><body>'
    + '<h1>' + escapeHtml(title) + '</h1>' + body
    + '</body></html>';
}

/** 所有失败路径都走这里，保证 action 永远返回一个字符串。 */
function note(title, text) {
  return page(title, '<p class="note">' + escapeHtml(text) + '</p>');
}

function describe(res) {
  if (res.status === 0) return res.error ? String(res.error) : '网络不可用';
  return 'HTTP ' + res.status;
}

/** 星级：★ 重复 rating.value 次，最多 5 颗。 */
function stars(value) {
  var n = parseInt(value, 10);
  if (isNaN(n) || n <= 0) return '';
  if (n > 5) n = 5;
  var out = '';
  for (var i = 0; i < n; i++) out += '★';
  return '<div class="stars">' + out + '</div>';
}

/** 从 suggest 结果里取豆瓣条目 id（形如 https://movie.douban.com/subject/1292052/?suggest=...）。 */
function subjectId(url) {
  if (!url) return '';
  var matched = /subject\/(\d+)/.exec(String(url));
  return matched ? matched[1] : '';
}

/**
 * 优先选 type 与当前条目类型（movie/tv）一致的那一项；没有就退回第一个能解析出 id 的。
 * 不按标题做二次比对：suggest 已经按相关度排过，再比一次反而会把「某某 第二季」筛掉。
 */
function pickSubject(items, mediaType) {
  var fallback = '';
  for (var i = 0; i < items.length; i++) {
    var candidate = items[i];
    if (!candidate || typeof candidate !== 'object') continue;
    var id = subjectId(candidate.url);
    if (id === '') continue;
    if (fallback === '') fallback = id;
    if (mediaType !== '' && candidate.type && String(candidate.type) === mediaType) return id;
  }
  return fallback;
}

function wantedCount() {
  var n = parseInt(webhtv.config ? webhtv.config.count : '', 10);
  if (isNaN(n) || n <= 0) n = DEFAULT_COUNT;
  if (n > MAX_COUNT) n = MAX_COUNT;
  return n;
}

/**
 * 宿主保证 webhtv.request 返回对象而不抛异常，这里再防御一次：
 * 万一拿到 null（比如宿主侧回调实现换了），脚本不能因此崩成「插件被隔离」。
 */
function get(url, headers) {
  var res = webhtv.request('GET', url, headers);
  if (!res || typeof res !== 'object') return { status: 0, error: 'empty response' };
  return res;
}

function parseJson(text, fallback) {
  try {
    var parsed = JSON.parse(text);
    return parsed === null || parsed === undefined ? fallback : parsed;
  } catch (error) {
    return fallback;
  }
}

function render(title, list) {
  var cards = '';
  for (var i = 0; i < list.length; i++) {
    var item = list[i];
    if (!item || typeof item !== 'object') continue;
    var user = item.user && typeof item.user === 'object' ? item.user : {};
    var rating = item.rating && typeof item.rating === 'object' ? item.rating : {};
    var meta = [];
    meta.push(user.name ? escapeHtml(user.name) : '匿名用户');
    if (item.create_time) meta.push(escapeHtml(item.create_time));
    if (typeof item.vote_count === 'number') meta.push(escapeHtml(item.vote_count) + ' 有用');
    cards += '<li class="card">'
      + '<div class="head">' + meta.join(' · ') + '</div>'
      + stars(rating.value)
      + '<p class="text">' + escapeHtml(item.comment || '') + '</p>'
      + '</li>';
  }
  return page('豆瓣短评 · ' + title, '<ul class="cards">' + cards + '</ul>');
}

webhtv.action('douban.comments', function (payload) {
  var title = '豆瓣短评';
  var context = {};
  var parsed = parseJson(payload, {});
  if (parsed && typeof parsed === 'object' && parsed.context && typeof parsed.context === 'object') {
    context = parsed.context;
  }
  var name = context.title ? String(context.title) : '';
  if (name === '') {
    return note(title, '宿主没有传片名。请确认插件声明了 plugin.data.episode 权限，并从一个详情页或播放页打开这个面板。');
  }

  var searchHeaders = { 'User-Agent': UA, 'Referer': 'https://movie.douban.com/' };
  var suggest = get(SUGGEST_URL + encodeURIComponent(name), searchHeaders);
  if (suggest.status !== 200) {
    return note(title, '搜索「' + name + '」失败：' + describe(suggest) + '。');
  }
  var items = parseJson(suggest.body, null);
  if (!items || typeof items.length !== 'number') {
    return note(title, '搜索「' + name + '」返回的不是数组，豆瓣可能改了接口或触发了风控。');
  }
  var id = pickSubject(items, context.mediaType ? String(context.mediaType) : '');
  if (id === '') {
    return note(title, '在豆瓣没有搜到「' + name + '」的条目。');
  }

  var detailHeaders = { 'User-Agent': UA, 'Referer': 'https://m.douban.com/movie/subject/' + id + '/' };
  var interests = get(INTERESTS_URL + id + '/interests?count=' + wantedCount() + '&start=0', detailHeaders);
  if (interests.status !== 200) {
    return note(title, '读取「' + name + '」的短评失败：' + describe(interests) + '。');
  }
  var body = parseJson(interests.body, null);
  if (!body || typeof body !== 'object') {
    return note(title, '短评接口返回的不是 JSON 对象。');
  }
  var list = body.interests && typeof body.interests.length === 'number' ? body.interests : [];
  if (list.length === 0) {
    return note(title, '「' + name + '」还没有短评。');
  }
  return render(name, list);
});
