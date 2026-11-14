# 存储星图 MCP 接口

App 内自带一个 MCP（Model Context Protocol）服务，把本地存储暴露成 13 个工具，
让本机或局域网里的 AI 客户端读目录、读文件、写中文备注。

- 协议版本：`2024-11-05`
- 传输：HTTP + JSON-RPC 2.0，单端点 `POST /mcp`
- 请求体上限：512 KB
- 实现：`src/com/dshgo/storageatlas/McpServer.java`（纯 `java.net` + `org.json`，无第三方库）

## 开启与鉴权

App 右上角菜单 →「AI 助手（MCP）」：

| 开关 | 默认 | 说明 |
|---|---|---|
| AI 接口服务 | 关 | 前台服务，开启后监听端口 |
| 端口 | 8765 | 1024–65535，改完自动重启服务 |
| 允许局域网访问 | 关 | 开启后监听 `0.0.0.0`，否则只听 `127.0.0.1` |
| 允许 AI 写备注 / 改重要度 | 关 | 关闭时写入类工具返回错误 |
| 允许 AI 删除文件 | 关 | 关闭时 `delete_path` 返回错误 |

请求必须带令牌（App 内生成、可复制）：

```http
Authorization: Bearer <令牌>
```

也支持查询参数 `?token=<令牌>`，方便在浏览器里试。

`GET /mcp` 返回一段摘要 JSON（服务名、协议版本、工具数量），可以拿来做连通性检查。

## 协议方法

| 方法 | 说明 |
|---|---|
| `initialize` | 返回协议版本、能力、serverInfo 和一段中文 instructions |
| `notifications/initialized` | 通知，返回 202 空响应 |
| `ping` | 返回空对象 |
| `tools/list` | 返回 13 个工具的名称、描述和 JSON Schema |
| `tools/call` | 调用工具，参数放在 `params.arguments` |

## 工具

### 读取

| 工具 | 参数 | 说明 |
|---|---|---|
| `storage_overview` | `deep`（默认 true） | 根目录、条目数、六档重要度计数、备注数。`deep` 为真时扫全盘，返回已备注 / 未备注 / 扫描条数 / 耗时，结果缓存 5 分钟 |
| `list_dir` | `path`、`limit`（≤1000）、`offset`、`sort`、`unannotated`、`include_hidden` | 列目录，返回 `total / returned / truncated / nextOffset` |
| `read_file` | `path`、`max_bytes`（≤65536）、`mode` | 文本读开头，二进制返回格式、体积、修改时间 |
| `read_files` | `paths`（≤20）、`max_bytes`（≤8192）、`mode` | 批量读，单项失败不影响其他项 |
| `search_files` | `query`、`under`、`ext`、`level`、`limit`（≤200） | 按名称、备注、相对路径匹配 |
| `annotate_guide` | — | 返回写备注的完整规则 |

`sort` 取值 `name` / `mtime` / `size`；`mode` 取值 `text` / `meta`。

### 写入（需在 App 内开启对应开关）

| 工具 | 参数 | 说明 |
|---|---|---|
| `set_note` | `path`、`note` | 空字符串清除备注 |
| `set_notes` | `items`（≤200） | 返回 `submitted / ok / created / updated / cleared / unchanged / failed / warnings / totalNoted`。`warnings` 指出不合规的备注（过长、过短、带标点、复述文件名），只提示不阻断 |
| `set_level` | `path`+`level`，或 `items`（≤200） | 重要度 0–5 |
| `set_favorite` | `path`、`on` | 收藏 / 取消收藏 |
| `create_folder` | `path` | 新建文件夹 |
| `rename_path` | `path`、`new_name` | 只改最后一段 |
| `delete_path` | `path`、`confirm`（必须 true） | 需要删除开关；不进回收站 |

重要度取值：`0` 系统、`1` 珍贵、`2` 项目、`3` 普通、`4` 可清、`5` 空置。

## 示例

curl：

```bash
curl -s http://127.0.0.1:8765/mcp \
  -H 'Authorization: Bearer <令牌>' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_dir","arguments":{"path":"/storage/emulated/0/Download","unannotated":true}}}'
```

Node（20 以上自带 fetch）：

```js
const r = await fetch("http://127.0.0.1:8765/mcp", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Authorization": "Bearer <令牌>",
  },
  body: JSON.stringify({
    jsonrpc: "2.0", id: 1, method: "tools/call",
    params: { name: "set_notes", arguments: { items: [
      { path: "/storage/emulated/0/Download/a.pdf", note: "电子书扫描件" },
    ] } },
  }),
});
console.log((await r.json()).result.content[0].text);
```

## 客户端配置

App 里可以直接复制这段（端口、令牌、地址都已填好）：

```json
{
  "mcpServers": {
    "storage-atlas": {
      "type": "http",
      "url": "http://127.0.0.1:8765/mcp",
      "headers": { "Authorization": "Bearer <令牌>" }
    }
  }
}
```

只支持 stdio 的客户端，用一个薄转发进程把 stdio 上的 JSON-RPC 转到上面的 HTTP 端点即可。

## 返回与错误

成功：

```json
{ "jsonrpc": "2.0", "id": 1,
  "result": { "content": [{ "type": "text", "text": "…" }], "isError": false } }
```

工具内部报错不会变成 HTTP 错误，而是 `isError: true` 加一段 `错误：…` 文本，AI 可以直接读。
协议层错误用标准 JSON-RPC 错误码：`-32700` 解析失败、`-32601` 未知方法。
鉴权失败返回 401，路径不是 `/mcp` 或 `/` 返回 404。

## 调用建议

1. 先 `storage_overview` 看全盘还有多少没备注。
2. 大目录用 `list_dir` 的 `offset` 翻页，或 `unannotated: true` 只看没写过的。
3. 判断文件是什么：`read_files` 批量读开头比一个个 `read_file` 快得多。
4. 最后 `set_notes` 一次写入，看 `warnings` 修掉不合规的备注。

## 安全

- 默认只听 `127.0.0.1`，手机外的设备碰不到。
- 写入、删除是两个独立开关，默认都关。
- 令牌存在 App 私有存储里，卸载即失效。
- 局域网模式只适合可信网络，公开 Wi-Fi 上用完请关掉。
