# 内置 Drpy 配置说明

本项目已将 drpy 核心库内置到 App 中，无需依赖远程服务器即可使用 drpy 规则。

## 内置文件位置

内置在 `quickjs/src/main/assets/js/lib/` 目录：
- `drpy2.js` - drpy2 核心库（73KB）
- `drpy2.min.js` - drpy2 核心库（同上，向后兼容）
- `drpy-core-lite.min.js` - drpy-core-lite 库（692KB）

## 使用方法

drpy 规则可以通过**两种方式**加载内置核心库，都能正常工作：

### 方式 1：使用 assets:// 协议（推荐）

```json
{
  "key": "drpy2_豆瓣",
  "name": "豆瓣(DR2)",
  "type": 3,
  "api": "assets://js/lib/drpy2.min.js",
  "ext": "http://101.43.28.189:5757/js/豆瓣.js"
}
```

**优点**：
- ✅ 直接从 assets 加载，最快
- ✅ 不依赖任何服务
- ✅ 离线可用

### 方式 2：使用内置服务器地址（向后兼容）

```json
{
  "key": "drpy2_豆瓣",
  "name": "豆瓣(DR2)",
  "type": 3,
  "api": "http://127.0.0.1:9978/js/lib/drpy2.js",
  "ext": "http://101.43.28.189:5757/js/豆瓣.js"
}
```

**说明**：
- ✅ 与旧配置兼容，无需修改
- ✅ 内置服务器会自动从 assets 加载文件
- ⚠️ 需要内置服务器运行（通常自动启动）

## 文件说明

| 文件名 | 大小 | 说明 |
|--------|------|------|
| `drpy2.js` | 73KB | 主要文件，支持 `/js/lib/drpy2.js` 路径 |
| `drpy2.min.js` | 73KB | 同上内容，支持 `/js/lib/drpy2.min.js` 路径 |
| `drpy-core-lite.min.js` | 692KB | drpy-core-lite 核心库 |

**注意**：`drpy2.js` 和 `drpy2.min.js` 是同一个文件的副本，提供了两种路径访问方式。

## 关于 127.0.0.1:9978

`127.0.0.1:9978` 是 App 内置的 HTTP 服务器地址，它会：
1. 自动从 `assets/` 目录加载文件
2. 响应 `/js/lib/drpy2.js` 等路径请求
3. 提供本地代理和其他服务

**两种路径对比**：

```
直接访问：assets://js/lib/drpy2.min.js
         → 直接读取 assets，最快

服务器访问：http://127.0.0.1:9978/js/lib/drpy2.js
         → 通过内置服务器 → 读取 assets
```

## 配置迁移指南

如果你的配置使用了远程地址，可以选择以下任一方式迁移：

### 不修改配置（推荐）
由于我们已经在 assets 中同时提供了 `drpy2.js` 和 `drpy2.min.js`，以下配置都能正常工作：

```json
"api": "http://127.0.0.1:9978/js/lib/drpy2.js"        // ✅ 可用
"api": "http://127.0.0.1:9978/js/lib/drpy2.min.js"    // ✅ 可用
```

### 迁移到 assets 协议（可选）
如果想要最佳性能，可以修改为：

```json
"api": "assets://js/lib/drpy2.min.js"                 // ✅ 推荐
"api": "assets://js/lib/drpy2.js"                     // ✅ 也可以
```

## drpy-core-lite 配置

如果规则依赖 drpy-core-lite：

```json
{
  "key": "drpy_lite_规则名",
  "name": "规则名(DR-Lite)",
  "type": 3,
  "api": "assets://js/lib/drpy-core-lite.min.js",
  "ext": "规则配置"
}
```

或通过服务器访问：
```json
{
  "key": "drpy_lite_规则名",
  "name": "规则名(DR-Lite)",
  "type": 3,
  "api": "http://127.0.0.1:9978/js/lib/drpy-core-lite.min.js",
  "ext": "规则配置"
}
```

## 更新 drpy 核心库

如果需要更新内置的 drpy 核心库：

1. 复制新版本文件到：
   ```
   quickjs/src/main/assets/js/lib/drpy2.min.js
   ```

2. 如果支持 `.js` 路径，也复制为：
   ```
   quickjs/src/main/assets/js/lib/drpy2.js
   ```

3. 重新编译 App

## 技术说明

- **Module.java**：支持 `assets://` 协议，直接从 assets 加载
- **Nano.java**：内置 HTTP 服务器，将 URL 路径映射到 assets 文件
- **缓存机制**：文件内容会被缓存，避免重复读取
- **混合使用**：可以部分规则用内置，部分规则用远程

## 参考 drpy-node 配置

本实现参考了 drpy-node 项目的 `DR2_API_TYPE` 配置：
- `DR2_API_TYPE=0`：从 public 目录加载（远程）
- `DR2_API_TYPE=1`：从 assets 加载（内置）

Android 端通过以下方式实现内置加载：
- 直接方式：`assets://` 协议
- 服务器方式：内置服务器自动从 assets 加载
