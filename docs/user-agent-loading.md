# User-Agent 加载机制文档

## 概述

本文档说明直播播放时 User-Agent 的加载优先级和实现机制。

## User-Agent 加载优先级

播放直播时，User-Agent 按以下优先级加载（从高到低）：

1. **M3U 文件中的 `#EXTVLCOPT:http-user-agent`** (最高优先级)
2. **配置文件中的 `headers` 数组** (根据 host 匹配)
3. **频道级别的 `ua` 配置** (Channel.ua)
4. **直播源级别的 `ua` 配置** (Live.ua)

## 实现细节

### 1. M3U 文件级别配置

**解析位置**: `LiveParser.java:170`

```java
else if (line.startsWith("#EXTVLCOPT:http-user-agent")) ua(line);
```

**示例**:
```m3u
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" group-title="央视",CCTV1
#EXTVLCOPT:http-user-agent=AptvPlayer-UA
http://example.com/stream.m3u8
```

### 2. 配置文件 headers 数组

**解析位置**: `LiveConfig.java:204`

**应用位置**: `Channel.java:349-364`

```java
String host = Uri.parse(getCurrent()).getHost();
if (host != null) {
    for (Header h : LiveConfig.get().getHeaders()) {
        if (host.equals(h.getHost())) {
            headers.putAll(Json.toMap(h.getHeader()));
            break;
        }
    }
}
```

**配置示例**:
```json
{
  "lives": [...],
  "headers": [
    {
      "host": "hlsztemgsplive.miguvideo.com",
      "header": {
        "User-Agent": "bingcha/1.1 (mianfeifenxiang)"
      }
    },
    {
      "host": "migu.188766.xyz",
      "header": {
        "User-Agent": "bingcha/1.1 (mianfeifenxiang)"
      }
    }
  ]
}
```

### 3. 频道级别配置

**继承位置**: `Channel.java:332`

```java
public void live(Live live) {
    if (!live.getUa().isEmpty() && getUa().isEmpty()) setUa(live.getUa());
    ...
}
```

**应用位置**: `Channel.java:360`

```java
if (!getUa().isEmpty()) headers.put(HttpHeaders.USER_AGENT, getUa());
```

### 4. 直播源级别配置

**配置示例**:
```json
{
  "lives": [
    {
      "name": "APTV",
      "type": 0,
      "url": "http://example.com/iptv.txt",
      "ua": "okhttp/3.15,AptvPlayer/1.4.0"
    }
  ]
}
```

## M3U 中为不同线路设置不同 UA

### 支持的方式

#### 方式 1: 使用 `#EXTVLCOPT` 标签（推荐）

```m3u
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" group-title="央视",CCTV1
#EXTVLCOPT:http-user-agent=UA-for-line1
http://line1.example.com/stream.m3u8
#EXTVLCOPT:http-user-agent=UA-for-line2
http://line2.example.com/stream.m3u8
#EXTVLCOPT:http-user-agent=UA-for-line3
http://line3.example.com/stream.m3u8
```

#### 方式 2: 使用 `ua=` 标签

```m3u
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" group-title="央视",CCTV1
ua=UA-for-line1
http://line1.example.com/stream.m3u8
ua=UA-for-line2
http://line2.example.com/stream.m3u8
```

#### 方式 3: 使用 `|` 分隔符（URL 内联参数）

```m3u
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" group-title="央视",CCTV1
http://line1.example.com/stream.m3u8|User-Agent=UA-for-line1
http://line2.example.com/stream.m3u8|User-Agent=UA-for-line2
```

### 已知限制

**当前实现的问题**:

在 `LiveParser.java:98-103` 中：

```java
} else if (!line.startsWith("#") && line.contains("://")) {
    String[] split = line.split("\\|");
    if (split.length > 1) setting.headers(Arrays.copyOfRange(split, 1, split.length));
    channel.getUrls().add(split[0]);
    setting.copy(channel).clear();  // ⚠️ 问题：将 UA 复制到 Channel 对象
}
```

**问题说明**: `setting.copy(channel)` 会将 UA 复制到 Channel 对象本身，而不是为每个 URL 单独存储配置。这导致：

- ❌ 切换线路时，UA 不会改变（所有线路使用同一个 UA）
- ✅ 只有解析时最后一个 URL 的 UA 会被保存到 Channel

**解决方案**: 需要为每个 URL 单独存储配置信息，而不是存储在 Channel 级别。

## 相关文件

- `app/src/main/java/com/github/tvbox/osc/api/LiveParser.java` - M3U 解析
- `app/src/main/java/com/github/tvbox/osc/api/config/LiveConfig.java` - 配置解析
- `app/src/main/java/com/github/tvbox/osc/bean/Channel.java` - 频道和 headers 处理
- `app/src/main/java/com/github/tvbox/osc/bean/Header.java` - headers 配置类
- `app/src/main/java/com/github/tvbox/osc/bean/Live.java` - 直播源配置

## 完整示例

### 配置文件示例

```json
{
  "lives": [
    {
      "name": "APTV",
      "type": 0,
      "url": "http://101.43.28.189:8080/iptv.txt",
      "epg": "http://epg.51zmt.top:8000/api/diyp/?ch={name}&date={date}",
      "logo": "https://epg.iill.top/logo/{name}.png",
      "ua": "okhttp/3.15,AptvPlayer/1.4.0",
      "timeout": 10
    }
  ],
  "headers": [
    {
      "host": "hlsztemgsplive.miguvideo.com",
      "header": {
        "User-Agent": "bingcha/1.1 (mianfeifenxiang)"
      }
    },
    {
      "host": "migu.188766.xyz",
      "header": {
        "User-Agent": "bingcha/1.1 (mianfeifenxiang)"
      }
    }
  ]
}
```

### M3U 文件示例

```m3u
#EXTM3U
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" http-user-agent="AptvPlayer-UA" group-title="央视IPV4",CCTV1
http://gslbmgsplive.miguvideo.com/wd_r2/cctv/cctv1hd/1200/index.m3u8
http://jiugong.wulinsy.cn:39901/tsfile/live/0001_1.m3u8
https://migu.188766.xyz/?migutoken=xxx&id=CCTV1&type=yy
```

### 实际加载结果

对于上述示例：

1. **第一个线路** (`gslbmgsplive.miguvideo.com`):
   - 使用 `headers` 配置中的 `"bingcha/1.1 (mianfeifenxiang)"`

2. **第二个线路** (`jiugong.wulinsy.cn`):
   - 使用 `lives.ua` 的 `"okhttp/3.15,AptvPlayer/1.4.0"`

3. **第三个线路** (`migu.188766.xyz`):
   - 使用 `headers` 配置中的 `"bingcha/1.1 (mianfeifenxiang)"`

## 更新日志

- 2025-11-19: 新增 `headers` 数组配置支持，实现基于 host 的 User-Agent 匹配
- 2025-11-19: 创建本文档
