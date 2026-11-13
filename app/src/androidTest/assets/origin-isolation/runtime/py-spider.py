import json
import urllib.parse
import urllib.request

ACCOUNT = ''
OPTIONS = {}
INITIALIZED = 0

class Spider:
    def init(self, extend=''):
        global ACCOUNT, OPTIONS, INITIALIZED
        OPTIONS = json.loads(extend) if isinstance(extend, str) else extend
        ACCOUNT = OPTIONS['account']
        INITIALIZED += 1
    def getName(self):
        return ACCOUNT + ':py:' + str(INITIALIZED)
    def fetch(self, path):
        req = urllib.request.Request(OPTIONS['base'] + path, headers=OPTIONS['headers'])
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode('utf-8'))
    def homeContent(self, filter):
        return {'class': [], 'list': [{'vod_id': 'origin-vod', 'vod_name': self.getName()}]}
    def homeVideoContent(self):
        return {'list': []}
    def categoryContent(self, tid, pg, filter, extend):
        return {'list': []}
    def detailContent(self, ids):
        return self.fetch('/detail?account=' + ACCOUNT + '&id=' + ids[0] + '&runtime=py')
    def searchContent(self, key, quick, pg='1'):
        return self.fetch('/search?account=' + ACCOUNT + '&runtime=py')
    def playerContent(self, flag, id, vipFlags):
        return self.fetch('/player?account=' + ACCOUNT + '&episode=' + urllib.parse.quote(id, safe='') + '&runtime=py')
    def manualVideoCheck(self):
        return False
    def isVideoFormat(self, url):
        return '.m3u8' in url or '.mkv' in url
    def action(self, action):
        return self.getName()
