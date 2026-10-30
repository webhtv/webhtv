// 示例插件：在宿主内容显示时记录一行诊断日志。
// 这是最小可用插件，用于验证装载、事件派发与故障隔离链路。

webhtv.on('ui.contentShown', function (payload) {
  var data = {};
  try { data = JSON.parse(payload || '{}'); } catch (e) {}
  webhtv.log('content shown on', data.activity || 'unknown');
  return 'ok';
});

webhtv.action('hello.show', function (payload) {
  webhtv.log('hello.show invoked with', payload);
  return 'ok';
});
