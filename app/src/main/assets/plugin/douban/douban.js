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
 *
 * 一次 action 要发 2 到 5 个请求（搜索 1 个、详情 1 个、短评 1 到 3 页）。真正卡住时间的是
 * 宿主给脚本的单次调用上限 5 秒（QuickJsPluginRuntime.CALL_TIMEOUT_MS，超时即禁用插件），
 * 而不是 manifest.runtime.timeout（那是**单个** HTTP 请求的上限，本清单设 2 秒）。
 * 实测四页合计 1.3 秒量级，余量充足；慢网络靠文件末尾的 canRequest() 自行收工。
 */

var UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
var SEARCH_URL = 'https://m.douban.com/rexxar/api/v2/search/subjects?q=';
var API_URL = 'https://m.douban.com/rexxar/api/v2/movie/';

/** 首屏条数（可在 manifest.params.count 里改）。 */
var DEFAULT_FIRST = 20;
/** 豆瓣 interests 接口单次最多给 50 条，别调大，多给的部分会被服务端静默截断。 */
var PAGE_SIZE = 50;
/** start 到 100 为止还有数据，start >= 101 返回 200 但 interests 是空数组 → 匿名上限 150 条。 */
var MAX_PAGES = 3;

/**
 * 缓存存活时间的两个档位，作为 webhtv.request 的第 5 个参数传下去（单位：秒）。
 *
 * 已完结的条目评论不再增长，可以放久一点；还在更新的条目天天有新短评，缓存一天就够。
 * 判断不出状态时按「可能在更新」处理——宁可多拿一次，也不要把用户按在新短评前面看不到。
 *
 * 宿主侧的 PluginHttpCache 负责落地：只缓存 GET/HEAD 的 200 响应，键含 pluginId、方法、URL、
 * 请求体与排序后的请求头，过期判据是文件 mtime。缓存命中的请求同样进审计日志，outcome 记为
 * "cache"，所以"有没有真的联网"在审计里可以分辨。
 */
var TTL_SETTLED = 2592000;
var TTL_RUNNING = 86400;

/**
 * 还允许**开始**一个请求的最后时刻（相对 action 开始，毫秒）。
 *
 * 宿主给脚本单次调用只留 5 秒（QuickJsPluginRuntime.CALL_TIMEOUT_MS），而超时的后果不是
 * "这次失败"——是把这个插件判成故障并**禁用**，用户此后再点 chip 只会毫无反应，直到重启
 * 应用或重新装载插件。所以本脚本必须自己保证收工，不能把慢网络甩给宿主的硬超时。
 *
 * 2600 + 单请求上限 2000 = 4600，距 5000ms 还剩 400ms 用来拼 HTML。
 * 这两个数字是配套的：调大任何一个都要重算，别只改一处。
 */
var LATEST_REQUEST_AT_MS = 2600;
/** 必须与 manifest.json 的 runtime.timeout 一致（2 秒）；改一处就要改另一处。 */
var REQUEST_TIMEOUT_MS = 2000;
var actionDeadline = 0;

var STYLE = ''
  + 'html,body{margin:0;padding:0;background:#f5f5f7;color:#1c1c1e;'
  + 'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Helvetica Neue",Arial,"PingFang SC","Microsoft YaHei",sans-serif;}'
  + 'body{padding:12px 12px 24px;}'
  + 'h1{font-size:16px;line-height:1.4;margin:0 0 12px;font-weight:600;}'
  + '.summary{background:#fff;border-radius:12px;padding:14px 16px;margin:0 0 12px;'
  + 'box-shadow:0 1px 2px rgba(0,0,0,.06);}'
  + '.score-row{display:flex;align-items:baseline;}'
  + '.score{font-size:30px;line-height:1.1;font-weight:700;color:#1c1c1e;}'
  + '.score-max{font-size:14px;color:#8e8e93;margin-left:2px;}'
  + '.score-stars{color:#f5a623;font-size:16px;letter-spacing:2px;margin-left:10px;}'
  + '.score-empty{font-size:16px;color:#8e8e93;}'
  + '.summary-meta{font-size:12px;color:#8e8e93;margin-top:6px;line-height:1.6;}'
  + 'ul.cards{list-style:none;margin:0;padding:0;}'
  + '.card{background:#fff;border-radius:12px;padding:12px;margin:0 0 10px;box-shadow:0 1px 2px rgba(0,0,0,.06);}'
  + '.card.off{display:none;}'
  + '.head{font-size:12px;color:#8e8e93;margin-bottom:6px;}'
  + '.stars{color:#f5a623;font-size:13px;letter-spacing:1px;}'
  + '.text{font-size:14px;line-height:1.65;margin:6px 0 0;white-space:pre-wrap;word-break:break-word;}'
  + '.more{display:block;width:100%;box-sizing:border-box;margin:4px 0 0;padding:12px;'
  + 'border:0;border-radius:12px;background:#fff;color:#0a84ff;font-size:14px;font-weight:600;'
  + 'box-shadow:0 1px 2px rgba(0,0,0,.06);outline:none;}'
  + '.more:focus{background:#e8f0fe;box-shadow:0 0 0 2px #0a84ff;}'
  + '.note{font-size:14px;line-height:1.7;color:#3a3a3c;background:#fff;border-radius:12px;padding:14px;}'
  + '.tail{font-size:12px;color:#8e8e93;text-align:center;padding:10px 0 0;line-height:1.6;}';

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

/** 星级：★ 重复 value 次，最多 5 颗（短评自带的 rating 是 5 分制）。 */
function stars(value) {
  var n = parseInt(value, 10);
  if (isNaN(n) || n <= 0) return '';
  if (n > 5) n = 5;
  var out = '';
  for (var i = 0; i < n; i++) out += '★';
  return '<div class="stars">' + out + '</div>';
}

/** 总评分是 10 分制，换算成 5 颗星来做视觉锚点：四舍五入后钳到 5。 */
function tenStars(value) {
  var n = Math.round(Number(value) / 2);
  if (isNaN(n) || n <= 0) return '';
  if (n > 5) n = 5;
  var out = '';
  for (var i = 0; i < n; i++) out += '★';
  return out;
}

/** 2463281 → 246.3万。数字太大时直接摆原数会让摘要行折成两行。 */
function formatCount(value) {
  var n = parseInt(value, 10);
  if (isNaN(n) || n <= 0) return '';
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿';
  if (n >= 10000) return (n / 10000).toFixed(1) + '万';
  return String(n);
}

/**
 * 从搜索响应的 subjects 容器里取出候选条目数组；形状不对一律返回空数组。
 *
 * search 接口的响应是 { types, banned, subjects:{ items:[...], total, start, count }, contents }，
 * subjects 是**对象**不是数组（旧的 subject_suggest 接口才是数组，2026-11 起它也失效了，
 * 对任何查询都回 200 + []）。这里逐层判空，任何一层形状异常都退化成"没搜到"，
 * 绝不让 TypeError 冒到宿主——宿主会把抛异常的插件判成故障并禁用。
 */
function subjectItems(payload) {
  if (!payload || typeof payload !== 'object') return [];
  var subjects = payload.subjects;
  if (!subjects || typeof subjects !== 'object') return [];
  var items = subjects.items;
  if (!items || typeof items.length !== 'number') return [];
  return items;
}

/**
 * 优先选类型与当前条目类型（movie/tv）一致的那一项；没有就退回第一条带有效数字 id 的。
 *
 * id 取 item.target.id（纯数字字符串，形如 "1291546"）；类型取 item.target_type，
 * 它的取值域恰好就是 movie/tv，与 payload.context.mediaType 相同，可以直接比。
 * 不按标题做二次比对：接口已按相关度排过，再比一次反而会把「某某 第二季」筛掉。
 */
function pickSubject(items, mediaType) {
  if (!items || typeof items.length !== 'number') return '';
  var fallback = '';
  for (var i = 0; i < items.length; i++) {
    var entry = items[i];
    if (!entry || typeof entry !== 'object') continue;
    var target = entry.target;
    if (!target || typeof target !== 'object') continue;
    var raw = target.id === null || target.id === undefined ? '' : String(target.id);
    if (!/^\d+$/.test(raw)) continue;
    if (fallback === '') fallback = raw;
    if (mediaType !== '' && entry.target_type && String(entry.target_type) === mediaType) return raw;
  }
  return fallback;
}

/** 首屏条数：manifest.params.count，缺省 20，最少 1，最多上限 150。 */
function firstScreenCount() {
  var n = parseInt(webhtv.config ? webhtv.config.count : '', 10);
  if (isNaN(n) || n <= 0) n = DEFAULT_FIRST;
  if (n > PAGE_SIZE * MAX_PAGES) n = PAGE_SIZE * MAX_PAGES;
  return n;
}

/**
 * 现在还能安全地再发一个请求吗。
 *
 * 判据是"还没到最晚开始时刻"，而不是"剩余时间够一次最坏请求"——后者会把慢一点点的
 * 首页搜索直接判成没时间，白白丢掉本来拿得到的短评。单个请求自己的 2 秒超时会兜住
 * 最坏情况，所以只要在 2600ms 之前出发，就绝不会撞上宿主那 5 秒。
 */
function canRequest() {
  return Date.now() < actionDeadline;
}

/**
 * 宿主保证 webhtv.request 返回对象而不抛异常，这里再防御一次：
 * 万一拿到 null（比如宿主侧回调实现换了），脚本不能因此崩成「插件被隔离」。
 *
 * 第 5 个参数是缓存存活秒数，宿主把它交给 PluginHttpCache 落地（见文件头注释）。
 * 传 0 或不传表示这次不碰缓存。
 */
function get(url, headers, ttlSeconds) {
  var res = webhtv.request('GET', url, headers, null, ttlSeconds);
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

/**
 * 该条目的缓存档位。
 *
 * 电影没有连载概念，直接按已完结算；剧集看 vendors[].episodes_info：
 * 出现「全」说明放完了，出现「更新至」说明还在更。两者都没有就按在更处理。
 */
function ttlOf(detail) {
  if (!detail || typeof detail !== 'object') return TTL_RUNNING;
  if (detail.is_tv !== true) return TTL_SETTLED;
  var vendors = detail.vendors;
  if (!vendors || typeof vendors.length !== 'number') return TTL_RUNNING;
  for (var i = 0; i < vendors.length; i++) {
    var vendor = vendors[i];
    if (!vendor || typeof vendor !== 'object') continue;
    var info = vendor.episodes_info ? String(vendor.episodes_info) : '';
    if (info !== '' && info.indexOf('全') >= 0) return TTL_SETTLED;
  }
  return TTL_RUNNING;
}

/** 顶部摘要：总评分（10 分制）+ 评价人数 + 短评总数。详情拿不到就只说短评总数。 */
function summary(detail, commentTotal) {
  var rating = detail && typeof detail === 'object' && detail.rating && typeof detail.rating === 'object'
    ? detail.rating : {};
  var score = Number(rating.value);
  var scoreHtml;
  if (!isNaN(score) && score > 0) {
    scoreHtml = '<div class="score-row">'
      + '<span class="score">' + escapeHtml(score.toFixed(1)) + '</span>'
      + '<span class="score-max">分</span>'
      + '<span class="score-stars">' + tenStars(score) + '</span>'
      + '</div>';
  } else {
    scoreHtml = '<div class="score-row"><span class="score-empty">暂无总评分</span></div>';
  }
  var meta = [];
  var votes = formatCount(rating.count);
  if (votes !== '') meta.push(votes + ' 人评价');
  var total = formatCount(commentTotal);
  if (total !== '') meta.push(total + ' 条短评');
  var metaHtml = meta.length === 0 ? '' : '<div class="summary-meta">' + meta.join(' · ') + '</div>';
  return '<div class="summary">' + scoreHtml + metaHtml + '</div>';
}

/**
 * 分页显隐：所有卡片一次性写进 DOM（含 class off），再由脚本按批揭开。
 *
 * 为什么不在滚到底时再请求：宿主只用 loadDataWithBaseURL 灌 HTML，没有
 * addJavascriptInterface，WebView 里的 JS 没有任何通道能回头调插件。所以数据必须
 * 在 action 返回前全部拿到，滚动只做显隐。
 */
function revealScript(step) {
  return '<script>(function(){'
    + 'var step=' + step + ';'
    + 'function reveal(){'
    + 'var off=document.querySelectorAll(".card.off");'
    + 'for(var i=0;i<off.length&&i<step;i++){off[i].className="card";}'
    + 'if(document.querySelectorAll(".card.off").length===0){'
    + 'var b=document.getElementById("douban-more");if(b){b.style.display="none";}'
    + '}'
    + '}'
    + 'var armed=true;'
    + 'function atBottom(){'
    + 'var el=document.documentElement;'
    + 'return (window.innerHeight+window.pageYOffset)>=(el.scrollHeight-64);'
    + '}'
    + 'window.addEventListener("scroll",function(){'
    + 'if(!atBottom()){armed=true;return;}'
    + 'if(!armed){return;}'
    + 'armed=false;'
    + 'reveal();'
    + '});'
    + 'var more=document.getElementById("douban-more");'
    + 'if(more){more.addEventListener("click",function(){reveal();});}'
    + '})();</script>';
}

function render(name, detail, list, commentTotal, total) {
  var first = firstScreenCount();
  var cards = '';
  var shown = 0;
  for (var i = 0; i < list.length; i++) {
    var item = list[i];
    if (!item || typeof item !== 'object') continue;
    var user = item.user && typeof item.user === 'object' ? item.user : {};
    var rating = item.rating && typeof item.rating === 'object' ? item.rating : {};
    var meta = [];
    meta.push(user.name ? escapeHtml(user.name) : '匿名用户');
    if (item.create_time) meta.push(escapeHtml(item.create_time));
    if (typeof item.vote_count === 'number') meta.push(escapeHtml(item.vote_count) + ' 有用');
    var hidden = shown >= first;
    shown = shown + 1;
    cards += '<li class="card' + (hidden ? ' off' : '') + '">'
      + '<div class="head">' + meta.join(' · ') + '</div>'
      + stars(rating.value)
      + '<p class="text">' + escapeHtml(item.comment || '') + '</p>'
      + '</li>';
  }
  // 页脚无论拿到多少条都要写：用户需要知道"这不是全部"，否则会以为豆瓣只有这么点短评。
  var tail = '<div class="tail">本页 ' + list.length + ' 条，豆瓣共 ' + formatCount(total) + ' 条<br>'
    + '匿名可看前 ' + PAGE_SIZE * MAX_PAGES + ' 条（豆瓣限制），更多需登录豆瓣</div>';
  var body = summary(detail, commentTotal)
    + '<ul class="cards">' + cards + '</ul>'
    + '<button id="douban-more" class="more"' + (shown > first ? '' : ' style="display:none"')
    + '>显示更多短评</button>'
    + tail;
  return page('豆瓣短评 · ' + name, body + revealScript(first));
}

webhtv.action('douban.comments', function (payload) {
  actionDeadline = Date.now() + LATEST_REQUEST_AT_MS;
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

  // Referer 必须带 m.douban.com：不带这条 rexxar 接口直接 400，而不是 200 + 空结果。
  var searchHeaders = { 'User-Agent': UA, 'Referer': 'https://m.douban.com/' };
  var search = get(SEARCH_URL + encodeURIComponent(name), searchHeaders, TTL_RUNNING);
  if (search.status !== 200) {
    return note(title, '搜索「' + name + '」失败：豆瓣搜索接口不可用（' + describe(search) + '）。稍后再点一次。');
  }
  var payloadItems = parseJson(search.body, null);
  if (!payloadItems || typeof payloadItems !== 'object') {
    return note(title, '搜索「' + name + '」返回的不是 JSON 对象，豆瓣可能改了接口或触发了风控。');
  }
  var id = pickSubject(subjectItems(payloadItems), context.mediaType ? String(context.mediaType) : '');
  if (id === '') {
    return note(title, '在豆瓣没有搜到「' + name + '」的条目。');
  }

  var detailHeaders = { 'User-Agent': UA, 'Referer': 'https://m.douban.com/movie/subject/' + id + '/' };
  // 详情请求本身只能用保守档位：拿到它才知道这片子是已完结还是在更。
  // 详情拿不到不算致命——只是顶部少了总评分，短评本身还值得显示。
  var detail = null;
  if (canRequest()) {
    var detailRes = get(API_URL + id, detailHeaders, TTL_RUNNING);
    if (detailRes.status === 200) detail = parseJson(detailRes.body, null);
  }
  var ttl = ttlOf(detail);

  var list = [];
  var total = 0;
  var start = 0;
  var failure = null;
  for (var p = 0; p < MAX_PAGES; p++) {
    // 时间不够就带着已拿到的部分收工，绝不把插件拖到宿主 5 秒硬超时上。
    if (!canRequest()) break;
    start = p * PAGE_SIZE;
    var res = get(API_URL + id + '/interests?count=' + PAGE_SIZE + '&start=' + start, detailHeaders, ttl);
    if (res.status !== 200) {
      // 第一页就失败说明接口不可用，直接报错；后续页失败只是少几条，不该吞掉已拿到的内容。
      if (list.length === 0) failure = res;
      break;
    }
    var body = parseJson(res.body, null);
    if (!body || typeof body !== 'object') {
      if (list.length === 0) failure = { status: -1 };
      break;
    }
    if (typeof body.total === 'number' && body.total > total) total = body.total;
    var chunk = body.interests && typeof body.interests.length === 'number' ? body.interests : [];
    for (var j = 0; j < chunk.length; j++) {
      if (list.length >= PAGE_SIZE * MAX_PAGES) break;
      list.push(chunk[j]);
    }
    if (chunk.length < PAGE_SIZE) break;
    if (total > 0 && list.length >= total) break;
  }

  if (failure !== null) {
    if (failure.status === -1) return note(title, '短评接口返回的不是 JSON 对象。');
    return note(title, '读取「' + name + '」的短评失败：' + describe(failure) + '。');
  }
  if (list.length === 0) {
    if (!canRequest()) {
      // 时间用完了但详情已经拿到：总评分是有效信息，别整页丢掉，只把短评位置换成提示。
      var hint = '读取「' + name + '」的短评超时了。网络太慢或豆瓣响应变慢，稍后再点一次。';
      if (detail !== null) {
        return page('豆瓣短评 · ' + name,
          summary(detail, detail.comment_count) + '<p class="note">' + escapeHtml(hint) + '</p>');
      }
      return note(title, hint);
    }
    return note(title, '「' + name + '」还没有短评。');
  }
  if (total === 0) {
    total = detail && typeof detail.comment_count === 'number' ? detail.comment_count : list.length;
  }
  return render(name, detail, list, total, total);
});
