import base64
import gzip
import hashlib
import io
import tokenize
import json
import os
import re
import socket
import time
import warnings
import zlib
from abc import ABCMeta, abstractmethod
from importlib.machinery import SourceFileLoader
from typing import Any, Dict, List
from urllib.parse import quote, unquote, urljoin

import requests
from lxml import etree

try:
    from urllib3 import encode_multipart_formdata
except ImportError:
    encode_multipart_formdata = None

try:
    from Crypto.Cipher import AES, PKCS1_v1_5 as PKCS1_cipher
    from Crypto.Util.Padding import unpad
    from Crypto.PublicKey import RSA
except ImportError:
    AES = None
    PKCS1_cipher = None
    unpad = None
    RSA = None

try:
    from com.github.tvbox.osc.util import LOG, PyUtil

    _ENV = 'T3'
    _log = LOG.e
except ImportError:
    PyUtil = None
    _ENV = 'T4'
    _log = print

try:
    from com.github.catvod import Proxy
except ImportError:
    Proxy = None

try:
    from com.chaquo.python import Python
except ImportError:
    Python = None

warnings.filterwarnings("ignore")
try:
    requests.packages.urllib3.disable_warnings()
except Exception:
    pass

_original_getaddrinfo = socket.getaddrinfo
_doh_cache: Dict[str, str] = {}


def _get_doh_url() -> str:
    try:
        from com.github.tvbox.osc import Setting

        doh_json = Setting.getDoh()
        if doh_json:
            doh_obj = json.loads(doh_json)
            return doh_obj.get("url", "")
    except Exception:
        pass
    return ""


def _doh_getaddrinfo(
    host: str,
    port: int,
    family: int = 0,
    type: int = 0,
    proto: int = 0,
    flags: int = 0,
):
    if host in {"doh.pub", "dns.alidns.com", "doh.360.cn"}:
        return _original_getaddrinfo(host, port, family, type, proto, flags)

    doh_url = _get_doh_url()
    if not doh_url:
        return _original_getaddrinfo(host, port, family, type, proto, flags)

    if host in _doh_cache:
        ip = _doh_cache[host]
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ip, port))]

    try:
        resp = requests.get(
            f"{doh_url}?name={host}&type=A",
            headers={"accept": "application/dns-json"},
            timeout=3,
        )
        data = resp.json()
        if "Answer" in data and data["Answer"]:
            ip = data["Answer"][0]["data"]
            _doh_cache[host] = ip
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ip, port))]
    except Exception:
        pass
    return _original_getaddrinfo(host, port, family, type, proto, flags)


socket.getaddrinfo = _doh_getaddrinfo


class BaseSpider(metaclass=ABCMeta):
    _instance = None
    ENV: str

    def __init__(self, query_params=None, t4_api=None):
        self.query_params = query_params or {}
        self.t4_api = t4_api or ''
        self.extend = ''
        self.ENV = _ENV
        self._cache = {}
        self.module = None
        self.log(f'BaseSpider __init__ t4_api:{t4_api}')

    def __new__(cls, *args, **kwargs):
        if cls._instance:
            return cls._instance
        cls._instance = super().__new__(cls)
        return cls._instance

    @abstractmethod
    def init(self, extend=""):
        pass

    @abstractmethod
    def homeContent(self, filter):
        pass

    @abstractmethod
    def homeVideoContent(self):
        pass

    @abstractmethod
    def categoryContent(self, tid, pg, filter, extend):
        pass

    @abstractmethod
    def detailContent(self, ids):
        pass

    @abstractmethod
    def searchContent(self, key, quick, pg=1):
        pass

    @abstractmethod
    def playerContent(self, flag, id, vipFlags=None):
        pass

    @abstractmethod
    def localProxy(self, params):
        pass

    @abstractmethod
    def isVideoFormat(self, url):
        pass

    @abstractmethod
    def manualVideoCheck(self):
        pass

    # @abstractmethod
    def getName(self):
        return 'BaseSpider'

    def init_api_ext_file(self):
        pass

    @staticmethod
    def _normalize_proxy_url(value):
        if value is None:
            return ''
        if not isinstance(value, str):
            value = f'{value}'
        value = value.strip()
        if not value:
            return ''
        if value.startswith(('http://', 'https://')):
            return value
        if 'hikerSkey' in value:
            return value
        return ''

    def getProxyUrl(self, flag=False):
        """Return a valid proxy endpoint compatible with both T3 and T4 runtimes."""
        env = (self.ENV or "").lower()
        candidates = []
        if env == 't4':
            candidates.append(('t4_api', self.t4_api))
        if PyUtil is not None:
            try:
                candidates.append(('PyUtil.getProxy', PyUtil.getProxy(flag)))
            except Exception:
                pass
        if Proxy is not None:
            try:
                candidates.append(('Proxy.getUrl', f'{Proxy.getUrl(flag)}?do=py'))
            except Exception:
                pass
        for source, candidate in candidates:
            proxy_url = self._normalize_proxy_url(candidate)
            if proxy_url:
                return proxy_url
            if candidate not in (None, ''):
                self.log(f'Ignore invalid proxy url from {source}: {candidate}')
        return ''

    def getDependence(self):
        return []

    def setExtendInfo(self, extend):
        self.extend = extend

    def _store_local_cache(self, key, value, expire=None):
        self._cache[key] = {
            'value': value,
            'expire': time.time() + expire if expire else None
        }
        return 'succeed'

    def _load_local_cache(self, key):
        item = self._cache.get(key)
        if not item:
            return None
        expires = item.get('expire')
        if expires and time.time() > expires:
            self._cache.pop(key, None)
            return None
        return item.get('value')

    def cleanup(self):
        """Remove expired items from the in-memory cache."""
        current_time = time.time()
        expired_keys = [
            key for key, item in self._cache.items()
            if item.get('expire') and current_time > item['expire']
        ]
        for key in expired_keys:
            self._cache.pop(key, None)

    def setCache(self, key, value, expire=None):
        if expire:
            return self._store_local_cache(key, value, expire)

        prepared = value
        if isinstance(prepared, (dict, list)):
            prepared = json.dumps(prepared, ensure_ascii=False)
        elif isinstance(prepared, (int, float)):
            prepared = str(prepared)
        elif prepared is None:
            prepared = ''
        elif isinstance(prepared, bytes):
            prepared = prepared.decode('utf-8', errors='ignore')
        else:
            prepared = str(prepared)

        if Proxy is not None:
            try:
                res = self.post(
                    f'http://127.0.0.1:{Proxy.getPort()}/cache?do=set&key={key}',
                    data={"value": prepared},
                    timeout=5,
                )
                if res.status_code == 200:
                    return 'succeed'
            except Exception:
                pass
        return self._store_local_cache(key, value, expire)

    def getCache(self, key):
        if Proxy is not None:
            try:
                value = self.fetch(
                    f'http://127.0.0.1:{Proxy.getPort()}/cache?do=get&key={key}',
                    timeout=5,
                ).text
                if value:
                    if (
                        (value.startswith('{') and value.endswith('}'))
                        or (value.startswith('[') and value.endswith(']'))
                    ):
                        try:
                            parsed = json.loads(value)
                            if isinstance(parsed, dict):
                                expires_at = parsed.get('expiresAt')
                                if expires_at and expires_at < int(time.time()):
                                    self.delCache(key)
                                    return None
                                return parsed
                            return parsed
                        except Exception:
                            pass
                    return value
            except Exception:
                pass
        return self._load_local_cache(key)

    def delCache(self, key):
        if Proxy is not None:
            try:
                res = self.fetch(
                    f'http://127.0.0.1:{Proxy.getPort()}/cache?do=del&key={key}',
                    timeout=5,
                )
                if res.status_code == 200:
                    return 'succeed'
            except Exception:
                pass
        self._cache.pop(key, None)
        return 'succeed'

    def regStr(self, src, reg, group=1):
        m = re.search(reg, src)
        src = ''
        if m:
            src = m.group(group)
        return src

    def custom_RegexGetText(self, Text, RegexText, Index, find_all=False):
        """Return either the first regex match or all matches based on the flag."""
        if not find_all:
            match = re.search(RegexText, Text, re.M | re.S)
            return match.group(Index) if match else ""
        return [m.group(Index) for m in re.finditer(RegexText, Text, re.M | re.S)]

    # cGroup = re.compile('[\U00010000-\U0010ffff]')
    # clean = cGroup.sub('',rsp.text)
    def cleanText(self, src):
        clean = re.sub('[\U0001F600-\U0001F64F\U0001F300-\U0001F5FF\U0001F680-\U0001F6FF\U0001F1E0-\U0001F1FF]', '',
                       src)
        return clean

    def fetch(self, url, params=None, headers=None, cookies=None, timeout=5, verify=True,
              allow_redirects=True, stream=None):
        rsp = requests.get(url, params=params, headers=headers, cookies=cookies, timeout=timeout,
                           verify=verify,
                           allow_redirects=allow_redirects, stream=stream)
        rsp.encoding = 'utf-8'
        return rsp

    def post(self, url, data=None, headers=None, cookies=None, timeout=5, verify=True, allow_redirects=True,
             stream=None):
        rsp = requests.post(url, data=data, headers=headers, cookies=cookies, timeout=timeout, verify=verify,
                            allow_redirects=allow_redirects, stream=stream)
        rsp.encoding = 'utf-8'
        return rsp

    def postJson(self, url, json, headers=None, cookies=None, timeout=5, verify=True, allow_redirects=True,
                 stream=None):
        rsp = requests.post(url, json=json, headers=headers, cookies=cookies, timeout=timeout, verify=verify,
                            allow_redirects=allow_redirects, stream=stream)
        rsp.encoding = 'utf-8'
        return rsp

    def postBinary(self, url, data: dict, boundary=None, headers=None, cookies=None, timeout=5, verify=True,
                   allow_redirects=True, stream=None):
        if encode_multipart_formdata is None:
            raise RuntimeError('encode_multipart_formdata is unavailable in this environment')
        if boundary is None:
            boundary = f'--dio-boundary-{int(time.time())}'
        if headers is None:
            headers = {}
        headers['Content-Type'] = f'multipart/form-data; boundary={boundary}'
        fields = []
        for key, value in data.items():
            fields.append((key, (None, value, None)))
        m = encode_multipart_formdata(fields, boundary=boundary)
        data = m[0]
        rsp = requests.post(url, data=data, headers=headers, cookies=cookies, timeout=timeout, verify=verify,
                            allow_redirects=allow_redirects, stream=stream)
        rsp.encoding = 'utf-8'
        return rsp

    def html(self, content):
        return etree.HTML(content)

    def xpText(self, root, expr):
        ele = root.xpath(expr)
        if len(ele) == 0:
            return ''
        else:
            return ele[0]

    def loadSpider(self, name, file_name=None):
        module = self.loadModule(name, file_name)
        if hasattr(module, 'Spider'):
            return module.Spider()
        if hasattr(module, 'BaseSpider'):
            return module.BaseSpider()
        raise AttributeError(f'Module {name} does not provide a Spider class')

    def loadModule(self, name, file_name=None):
        if file_name:
            return SourceFileLoader(name, file_name).load_module()
        if Python is None:
            raise RuntimeError('Chaquopy Python runtime is not available')
        cache_dir = Python.getPlatform().getApplication().getCacheDir().getAbsolutePath()
        path = os.path.join(os.path.join(cache_dir, 'py'), f'{name}.py')
        return SourceFileLoader(name, path).load_module()

    # ==================== Static helpers ======================
    def log(self, msg):
        if isinstance(msg, dict) or isinstance(msg, list):
            msg = self.json2str(msg)
        else:
            msg = f'{msg}'

        _log(msg)

    @staticmethod
    def isVideo():
        pass

    @staticmethod
    def adRemove():
        pass

    @staticmethod
    def replaceAll(text, mtext, rtext):
        return re.sub(mtext, rtext, text)

    @staticmethod
    def str2json(str):
        return json.loads(str)

    @staticmethod
    def json2str(str):
        return json.dumps(str, ensure_ascii=False)

    @staticmethod
    def encodeStr(input, encoding='GBK'):
        return quote(input.encode(encoding, 'ignore'))

    @staticmethod
    def decodeStr(input, encoding='GBK'):
        return unquote(input, encoding)

    @staticmethod
    def hexStringTobytes(_str):
        _str = _str.replace(" ", "")
        return bytes.fromhex(_str)

    @staticmethod
    def bytesToHexString(_bytes, no_space=True):
        _str = ''.join(['%02X ' % b for b in _bytes])
        if no_space:
            _str = _str.replace(" ", "")
        return _str

    @staticmethod
    def urljoin(base_url, path):
        return urljoin(base_url, path)

    @staticmethod
    def coverDict2form(data: dict):
        forms = []
        for k, v in data.items():
            forms.append(f'{k}={v}')
        return '&'.join(forms)

    @staticmethod
    def buildUrl(url: str, obj: dict = None):
        if obj is None:
            return url
        if '?' in url:
            old_query = url.split('?')[1]
            old_params = {}
            for text in old_query.split('&'):
                key = text.split('=')[0]
                value = text.split('=')[1]
                old_params[key] = value
        else:
            old_params = {}

        new_obj = old_params.copy()
        new_obj.update(obj)
        param_list = [f'{i}={new_obj[i]}' for i in new_obj]
        prs = '&'.join(param_list)
        if param_list:
            url = url.split('?')[0] + '?' + prs
        return url

    @staticmethod
    def to_lower_camel_case(x):
        """Convert snake_case to lowerCamelCase."""
        s = re.sub('_([a-zA-Z])', lambda m: (m.group(1).upper()), x)
        return s[0].lower() + s[1:]

    @staticmethod
    def md5(text):
        return hashlib.md5(text.encode(encoding='UTF-8')).hexdigest()

    @staticmethod
    def gzinflate(compressed: bytes) -> bytes:
        return zlib.decompress(compressed, -zlib.MAX_WBITS)

    @staticmethod
    def gzipCompress(compressed: bytes) -> bytes:
        return gzip.decompress(compressed)

    @staticmethod
    def gzip(input_str: str) -> str:
        try:
            utf8_bytes = input_str.encode('utf-8')

            compressed_data = zlib.compress(utf8_bytes, level=zlib.Z_BEST_COMPRESSION, wbits=31)

            b64_data = base64.b64encode(compressed_data).decode('ascii')

            return b64_data

        except Exception as e:
            raise ValueError(f'gzip compression failed: {e}')

    @staticmethod
    def ungzip(b64_data: str) -> str:
        try:
            compressed_data = base64.b64decode(b64_data)

            decompressed_data = zlib.decompress(compressed_data, zlib.MAX_WBITS | 32)

            return decompressed_data.decode('utf-8')

        except Exception as e:
            raise ValueError(f'gzip decompression failed: {e}')

    # Helper utilities
    @staticmethod
    def utf8_array_to_str(data: List[int]) -> str:
        """Convert an iterable of integer byte values into a UTF-8 string."""
        byte_array = bytes(data)
        return byte_array.decode('utf-8')

    @staticmethod
    def bytes2stream(some_bytes: bytes):
        return io.BytesIO(some_bytes)

    @staticmethod
    def stream2bytes(some_stream):
        return some_stream.read()

    def skip_bytes(self, some_bytes: bytes, pos=0) -> bytes:
        some_stream = self.bytes2stream(some_bytes)
        some_stream.seek(pos)
        return self.stream2bytes(some_stream)

    @staticmethod
    def base64Encode(text):
        return base64.b64encode(text.encode("utf8")).decode("utf-8")

    @staticmethod
    def base64Decode(text: str):
        return base64.b64decode(text).decode("utf-8")

    @staticmethod
    def atob(text):
        return base64.b64decode(text.encode("utf8")).decode("latin1")

    @staticmethod
    def btoa(text):
        return base64.b64encode(text.encode("latin1")).decode("utf8")

    @staticmethod
    def check_unsafe_attributes(string):
        g = tokenize.tokenize(io.BytesIO(string.encode('utf-8')).readline)
        pre_op = ''
        for toktype, tokval, _, _, _ in g:
            if toktype == tokenize.NAME and pre_op == '.' and tokval.startswith('_'):
                attr = tokval
                msg = "access to attribute '{0}' is unsafe.".format(attr)
                raise AttributeError(msg)
            elif toktype == tokenize.OP:
                pre_op = tokval

    @staticmethod
    def aes_cbc_decode(ciphertext, key, iv):
        """
        Decrypt ciphertext using AES-CBC with PKCS7 padding.
        """
        if AES is None or unpad is None:
            raise RuntimeError('pycryptodome is required for AES decryption')
        # Decode base64 payload into bytes
        ciphertext = base64.b64decode(ciphertext)
        # Create AES decryptor
        decrypter = AES.new(key.encode(), AES.MODE_CBC, iv.encode())
        # Decrypt and remove padding
        plaintext = decrypter.decrypt(ciphertext)
        plaintext = unpad(plaintext, AES.block_size)
        return plaintext.decode('utf-8')

    @staticmethod
    def rsa_private_decode(ciphertext, private_key, default_length=256):
        if RSA is None or PKCS1_cipher is None:
            raise RuntimeError('pycryptodome is required for RSA decryption')
        b64_ciphertext = ciphertext
        num_padding = 4 - (len(b64_ciphertext) % 4)
        if num_padding < 4:
            b64_ciphertext += "=" * num_padding
        ciphertext = base64.b64decode(b64_ciphertext)
        private_key = f'-----BEGIN RSA PRIVATE KEY-----\n{private_key}\n-----END RSA PRIVATE KEY-----'
        pri_Key = RSA.importKey(private_key)
        decrypter = PKCS1_cipher.new(pri_Key)
        # Decrypt in chunks when needed
        length = len(ciphertext)
        if length < default_length:
            plaintext = b''.join(decrypter.decrypt(ciphertext, b' '))
        else:
            offset = 0
            res = []
            while length - offset > 0:
                if length - offset > default_length:
                    res.append(decrypter.decrypt(ciphertext[offset:offset + default_length], b' '))
                else:
                    res.append(decrypter.decrypt(ciphertext[offset:], b' '))
                offset += default_length

            plaintext = b''.join(res)
        return plaintext.decode('utf-8')

    @staticmethod
    def rsa_public_encode(text, public_key, default_length=256):
        if RSA is None or PKCS1_cipher is None:
            raise RuntimeError('pycryptodome is required for RSA encryption')
        public_key = "-----BEGIN RSA PRIVATE KEY-----\n" + public_key + "\n-----END RSA PRIVATE KEY-----"
        pub_key = RSA.importKey(public_key)
        cipher = PKCS1_cipher.new(pub_key)
        text = text.encode("utf-8")
        length = len(text)
        if length < default_length:
            rsa_text = base64.b64encode(cipher.encrypt(text))
        else:
            offset = 0
            res = []
            while length - offset > 0:
                if length - offset > default_length:
                    res.append(cipher.encrypt(text[offset:offset + default_length]))
                else:
                    res.append(cipher.encrypt(text[offset:]))
                offset += default_length
            byte_data = b''.join(res)

            rsa_text = base64.b64encode(byte_data)

        ciphertext = rsa_text.decode("utf8")
        return ciphertext

    @staticmethod
    def remove_comments(text):
        pattern = re.compile(r'\s*[\'\"]{3}[\S\s]*?[\'\"]{3}')
        text = pattern.sub('', text)
        pattern = re.compile(r'\s*/\*[\S\s]*?\*/')
        text = pattern.sub('', text)
        text = text.splitlines()
        text = [txt for txt in text if not (txt.strip().startswith('//') or txt.strip().startswith('#'))]
        text = '\n'.join(text)
        return text.strip()

    # ==================== Advanced helpers ======================
    def superStr2dict(self, text: str):
        text = self.remove_comments(text)
        localdict = {'true': True, 'false': False, 'null': None}
        self.safe_eval(f'result={text}', localdict)
        result = localdict.get('result') or {}
        return result

    def fixAdM3u8(self, m3u8_text, m3u8_url='', ad_remove=''):
        if ad_remove.startswith('reg:'):
            ad_remove = ad_remove[4:]
        elif ad_remove.startswith('js:'):
            ad_remove = ad_remove[3:]
        else:
            ad_remove = None

        print(ad_remove)

        # Split into header, body, and footer sections
        m3u8_start = m3u8_text[:m3u8_text.find('#EXTINF')].strip()
        m3u8_body = m3u8_text[m3u8_text.find('#EXTINF'):m3u8_text.find('#EXT-X-ENDLIST')].strip()
        m3u8_end = m3u8_text[m3u8_text.find('#EXT-X-ENDLIST'):].strip()

        murls = []
        m3_body_list = m3u8_body.splitlines()
        m3_len = len(m3_body_list)
        i = 0
        while i < m3_len:
            mi = m3_body_list[i]
            mi_1 = m3_body_list[i + 1]
            if mi.startswith('#EXTINF'):
                murls.append('&'.join([mi, mi_1]))
                i += 2
            elif mi.startswith('#EXT-X-DISCONTINUITY'):
                mi_2 = m3_body_list[i + 2]
                murls.append('&'.join([mi, mi_1, mi_2]))
                i += 3
            else:
                break
        new_m3u8_body = []
        for murl in murls:
            if ad_remove and self.regStr(murl, ad_remove):
                pass
            else:
                murl_list = murl.split('&')
                if not murl_list[-1].startswith('http') and m3u8_url.startswith('http'):
                    murl_list[-1] = self.urljoin(m3u8_url, murl_list[-1])
                new_m3u8_body.extend(murl_list)

        new_m3u8_body = '\n'.join(new_m3u8_body).strip()
        m3u8_text = '\n'.join([m3u8_start, new_m3u8_body, m3u8_end]).strip()
        return m3u8_text

    def eval_computer(self, text):
        localdict = {}
        self.safe_eval(f'ret={text.replace("=", "")}', localdict)
        ret = localdict.get('ret') or None
        return ret

    def safe_eval(self, code: str = '', localdict: dict = None):
        code = code.strip()
        if not code:
            return {}
        if localdict is None:
            localdict = {}
        builtins = __builtins__
        if not isinstance(builtins, dict):
            builtins = builtins.__dict__.copy()
        else:
            builtins = builtins.copy()
        for key in ['__import__', 'eval', 'exec', 'globals', 'dir', 'copyright', 'open', 'quit']:
            del builtins[key]
        global_dict = {'__builtins__': builtins,
                       'json': json, 'print': print,
                       're': re, 'time': time, 'base64': base64
                       }
        try:
            self.check_unsafe_attributes(code)
            exec(code, global_dict, localdict)
            return localdict
        except Exception as e:
            return {'error': f'exec failed: {e}'}


Spider = BaseSpider
