import os
import sys
import requests
from importlib.machinery import SourceFileLoader
from urllib.parse import quote, urlparse, urlunparse
import json


def spider(cache, api):
    name = os.path.basename(api)
    path = cache + '/' + name
    download(path, api)
    name = name.split('.')[0]

    # Add the directory containing app.py to sys.path so spider files can import base/t4 modules
    app_dir = os.path.dirname(os.path.abspath(__file__))
    if app_dir not in sys.path:
        sys.path.insert(0, app_dir)

    try:
        module = SourceFileLoader(name, path).load_module()
        if not hasattr(module, 'Spider'):
            raise AttributeError(f"Module '{name}' loaded successfully but has no 'Spider' attribute. Module attributes: {dir(module)}")
        return module.Spider()
    except Exception as e:
        import traceback
        print(f"Error loading spider module '{name}' from '{path}':")
        print(f"sys.path: {sys.path}")
        print(f"Error: {e}")
        traceback.print_exc()
        raise


def encode_url(url):
    """
    Encode URL to handle Chinese characters and other special characters.
    Only encodes the path and query parts, preserving the scheme and netloc.
    """
    parsed = urlparse(url)
    # Encode the path, but preserve already encoded characters
    # Split path into segments and encode each segment
    path_parts = parsed.path.split('/')
    encoded_parts = [quote(part, safe='') for part in path_parts]
    encoded_path = '/'.join(encoded_parts)

    # Reconstruct the URL with encoded path
    encoded_url = urlunparse((
        parsed.scheme,
        parsed.netloc,
        encoded_path,
        parsed.params,
        parsed.query,
        parsed.fragment
    ))
    return encoded_url


def download(path, api):
    if api.startswith('http'):
        # Encode URL to handle Chinese characters and other special characters
        encoded_url = encode_url(api)
        writeFile(path, redirect(encoded_url).content)
    else:
        writeFile(path, str.encode(api))


def writeFile(path, content):
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    rsp = requests.get(url, allow_redirects=False, verify=False)
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
