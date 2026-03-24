import os
import hashlib
import requests
import sys
from importlib.machinery import SourceFileLoader
import json


def spider(cache, api, module_key=None):
    name, path = getModule(cache, api, module_key)
    error = None
    for _ in range(2):
        try:
            download(path, api)
            clearModule(name)
            module = SourceFileLoader(name, path).load_module()
            spider_cls = module.Spider
            abstract_methods = getattr(spider_cls, '__abstractmethods__', None)
            if abstract_methods:
                raise TypeError(f'Spider class is abstract: {sorted(list(abstract_methods))}')
            return spider_cls()
        except Exception as e:
            error = e
    raise error


def getModule(cache, api, module_key=None):
    source = api.split('#', 1)[0].split('?', 1)[0]
    filename = os.path.basename(source) or 'spider.py'
    root, ext = os.path.splitext(filename)
    if not ext:
        ext = '.py'
    identity = str(module_key or api)
    digest = hashlib.md5(identity.encode('utf-8')).hexdigest()
    name = f'{root}_{digest}'
    path = os.path.join(cache, f'{name}{ext}')
    return name, path


def download(path, api):
    if api.startswith('http'):
        writeFile(path, redirect(api).content)
    else:
        writeFile(path, str.encode(api))


def writeFile(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temp = f'{path}.tmp'
    with open(temp, 'wb') as f:
        f.write(content)
    os.replace(temp, path)


def clearModule(name):
    if name in sys.modules:
        del sys.modules[name]


def redirect(url):
    rsp = requests.get(url, allow_redirects=False, verify=False, timeout=10)
    rsp.raise_for_status()
    if 'Location' in rsp.headers:
        return redirect(rsp.headers['Location'])
    else:
        return rsp


def str2json(content):
    return json.loads(content)


def getDependence(ru):
    result = ru.getDependence()
    return result


def getName(ru):
    result = ru.getName()
    return result


def init(ru, extend=""):
    if hasattr(ru, 'setExtendInfo'):
        ru.setExtendInfo(extend)
    ru.init(extend)


def homeContent(ru, filter):
    result = ru.homeContent(filter)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def homeVideoContent(ru):
    result = ru.homeVideoContent()
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def categoryContent(ru, tid, pg, filter, extend):
    result = ru.categoryContent(tid, pg, filter, str2json(extend))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def detailContent(ru, array):
    result = ru.detailContent(str2json(array))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def searchContent(ru, key, quick, pg="1"):
    result = ru.searchContent(key, quick, pg)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def playerContent(ru, flag, id, vipFlags):
    result = ru.playerContent(flag, id, str2json(vipFlags))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def liveContent(ru, url):
    result = ru.liveContent(url)
    return result


def localProxy(ru, param):
    result = ru.localProxy(str2json(param))
    return result


def destroy(ru):
    if hasattr(ru, 'destroy'):
        ru.destroy()


def action(ru, action):
    result = ru.action(action)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def manualVideoCheck(ru):
    result = ru.manualVideoCheck()
    return result


def isVideoFormat(ru, url):
    result = ru.isVideoFormat(url)
    return result


def run():
    pass


if __name__ == '__main__':
    run()
