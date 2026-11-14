package com.dshgo.storageatlas;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简 MCP 服务器（JSON-RPC 2.0 over HTTP），只监听 127.0.0.1，供本机 AI 连接。
 *
 * 设计要点：
 * - 纯 java.net + org.json，不引第三方依赖；
 * - 所有路径都必须落在手机存储根目录内；
 * - 写备注默认允许，删除默认禁止（要用户在面板里单独打开）；
 * - 每次调用都记一条日志，App 面板里能看到 AI 干了什么。
 */
public class McpServer {

    public static final String PROTOCOL = "2024-11-05";
    private static final int MAX_BODY = 512 * 1024;

    private final Context ctx;
    private final Atlas atlas;
    private final List<String> log = Collections.synchronizedList(new ArrayList<String>());
    private Runnable onDataChanged;

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private Thread acceptThread;
    private volatile boolean running;
    private String port = "8765";
    private boolean lan = false;
    private String token = "";

    /** 全盘统计很慢（存储走 FUSE，几千条就要几秒），缓存起来反复查询就快了。 */
    private volatile JSONObject deepCache;
    private volatile long deepCacheAt;
    private static final long DEEP_TTL_MS = 5 * 60 * 1000L;

    public McpServer(Context c) {
        ctx = c.getApplicationContext();
        atlas = Atlas.get(ctx);
    }

    public void setOnDataChanged(Runnable r) { onDataChanged = r; }

    public boolean isRunning() { return running; }

    public String port() { return port; }

    public boolean lan() { return lan; }

    public String url() { return "http://" + (lan ? "0.0.0.0" : "127.0.0.1") + ":" + port + "/mcp"; }

    /** 局域网/本机 IPv4，用于在面板里显示真实可连地址。 */
    public static String localIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                java.net.NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress ia = addrs.nextElement();
                    if (ia.isLoopbackAddress() || !(ia instanceof java.net.Inet4Address)) continue;
                    String ip = ia.getHostAddress();
                    if (ip != null && !ip.startsWith("127.")) return ip;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public List<String> logLines() {
        synchronized (log) {
            List<String> copy = new ArrayList<>(log);
            Collections.reverse(copy);
            return copy;
        }
    }

    private void note(String s) {
        synchronized (log) {
            log.add(s);
            while (log.size() > 60) log.remove(0);
        }
    }

    private void changed() {
        if (onDataChanged != null) onDataChanged.run();
    }

    /** 写备注只改变“已备注/未备注”的计数，不必让整盘统计作废。 */
    private void deepAdjust(int annotatedDelta) {
        JSONObject d = deepCache;
        if (d == null || annotatedDelta == 0) return;
        synchronized (d) {
            int a = d.optInt("annotated", 0) + annotatedDelta;
            int s = d.optInt("scanned", 0);
            if (a < 0) a = 0;
            if (a > s) a = s;
            try {
                d.put("annotated", a);
                d.put("unannotated", s - a);
                d.put("incremental", true);
            } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------ 生命周期

    public synchronized void start(String portStr, String tokenStr) throws Exception {
        start(portStr, tokenStr, false);
    }

    public synchronized void start(String portStr, String tokenStr, boolean lanAccess) throws Exception {
        if (running) return;
        port = portStr == null || portStr.isEmpty() ? "8765" : portStr;
        token = tokenStr == null ? "" : tokenStr;
        lan = lanAccess;
        serverSocket = new ServerSocket(Integer.parseInt(port), 8,
                InetAddress.getByName(lanAccess ? "0.0.0.0" : "127.0.0.1"));
        running = true;
        pool = Executors.newFixedThreadPool(3);
        acceptThread = new Thread(new Runnable() {
            @Override public void run() { acceptLoop(); }
        }, "mcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        note("服务已启动 · " + (lan ? "局域网 " + localIp() + ":" + port : "127.0.0.1:" + port));
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) { }
        serverSocket = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
        note("服务已停止");
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = serverSocket.accept();
                pool.execute(new Runnable() {
                    @Override public void run() { handle(s); }
                });
            } catch (Exception e) {
                if (running) note("accept 失败：" + e.getClass().getSimpleName());
            }
        }
    }

    // ------------------------------------------------------------ HTTP

    private void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            String first = readLine(in);
            if (first == null || first.isEmpty()) { s.close(); return; }
            String[] parts = first.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "/";

            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            int contentLength = 0;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int i = line.indexOf(':');
                if (i > 0) {
                    String k = line.substring(0, i).trim().toLowerCase(Locale.ROOT);
                    String v = line.substring(i + 1).trim();
                    headers.put(k, v);
                    if (k.equals("content-length")) {
                        try { contentLength = Integer.parseInt(v); } catch (Exception ignored) { }
                    }
                }
            }

            String query = "";
            int qi = path.indexOf('?');
            if (qi >= 0) {
                query = path.substring(qi + 1);
                path = path.substring(0, qi);
            }

            if (!path.equals("/mcp") && !path.equals("/")) {
                send(out, 404, "{\"error\":\"not found\"}");
                s.close();
                return;
            }

            if (!authorized(headers, query)) {
                send(out, 401, "{\"error\":\"unauthorized\",\"hint\":\"带 Authorization: Bearer <令牌>\"}");
                s.close();
                return;
            }

            if (method.equals("GET")) {
                JSONObject info = new JSONObject();
                info.put("name", "存储星图 MCP");
                info.put("protocol", PROTOCOL);
                info.put("endpoint", "/mcp");
                info.put("tools", TOOL_NAMES.length);
                info.put("hint", "用 POST + JSON-RPC 2.0 调用，例如 tools/list");
                send(out, 200, info.toString());
                s.close();
                return;
            }

            if (contentLength > MAX_BODY) {
                send(out, 413, "{\"error\":\"body too large\"}");
                s.close();
                return;
            }
            byte[] body = new byte[Math.max(contentLength, 0)];
            int off = 0;
            while (off < body.length) {
                int n = in.read(body, off, body.length - off);
                if (n < 0) break;
                off += n;
            }

            String resp = dispatch(new String(body, StandardCharsets.UTF_8));
            if (resp == null) {
                send(out, 202, "");
            } else {
                send(out, 200, resp);
            }
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) { }
            note("请求失败：" + e.getClass().getSimpleName());
        }
    }

    private boolean authorized(Map<String, String> headers, String query) {
        if (token == null || token.isEmpty()) return true;
        String auth = headers.get("authorization");
        if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            if (auth.substring(7).trim().equals(token)) return true;
        }
        if (query.contains("token=" + token)) return true;
        return false;
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
            if (bos.size() > 8192) break;
        }
        if (c == -1 && bos.size() == 0) return null;
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void send(OutputStream out, int code, String body) throws Exception {
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : code == 401 ? "Unauthorized"
                : code == 404 ? "Not Found" : code == 202 ? "Accepted" : "Error") + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    // ------------------------------------------------------------ JSON-RPC

    private String dispatch(String body) throws Exception {
        JSONObject req;
        try {
            req = new JSONObject(body);
        } catch (Exception e) {
            return error(null, -32700, "无法解析的 JSON");
        }
        Object id = req.opt("id");
        String method = req.optString("method", "");
        JSONObject params = req.optJSONObject("params");
        if (params == null) params = new JSONObject();

        if (method.equals("notifications/initialized") || method.startsWith("notifications/")) {
            return null;
        }
        if (method.equals("initialize")) {
            JSONObject result = new JSONObject();
            try {
                result.put("protocolVersion", PROTOCOL);
                JSONObject caps = new JSONObject();
                caps.put("tools", new JSONObject());
                result.put("capabilities", caps);
                JSONObject info = new JSONObject();
                info.put("name", "storage-atlas");
                info.put("version", "1.2");
                result.put("serverInfo", info);
                result.put("instructions", GUIDE);
            } catch (Exception ignored) { }
            return ok(id, result);
        }
        if (method.equals("ping")) return ok(id, new JSONObject());
        if (method.equals("tools/list")) {
            JSONObject result = new JSONObject();
            try { result.put("tools", toolDefs()); } catch (Exception ignored) { }
            return ok(id, result);
        }
        if (method.equals("tools/call")) {
            String name = params.optString("name", "");
            JSONObject args = params.optJSONObject("arguments");
            if (args == null) args = new JSONObject();
            try {
                String text = callTool(name, args);
                note("AI 调用 " + name);
                JSONObject result = new JSONObject();
                JSONArray content = new JSONArray();
                JSONObject t = new JSONObject();
                t.put("type", "text");
                t.put("text", text);
                content.put(t);
                result.put("content", content);
                result.put("isError", false);
                return ok(id, result);
            } catch (Exception e) {
                note("AI 调用 " + name + " 失败：" + e.getMessage());
                JSONObject result = new JSONObject();
                JSONArray content = new JSONArray();
                JSONObject t = new JSONObject();
                t.put("type", "text");
                t.put("text", "错误：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                content.put(t);
                result.put("content", content);
                result.put("isError", true);
                return ok(id, result);
            }
        }
        return error(id, -32601, "未知方法：" + method);
    }

    private static String ok(Object id, JSONObject result) {
        JSONObject o = new JSONObject();
        try {
            o.put("jsonrpc", "2.0");
            o.put("id", id == null ? JSONObject.NULL : id);
            o.put("result", result);
        } catch (Exception ignored) { }
        return o.toString();
    }

    private static String error(Object id, int code, String msg) {
        JSONObject o = new JSONObject();
        try {
            o.put("jsonrpc", "2.0");
            o.put("id", id == null ? JSONObject.NULL : id);
            JSONObject e = new JSONObject();
            e.put("code", code);
            e.put("message", msg);
            o.put("error", e);
        } catch (Exception ignored) { }
        return o.toString();
    }

    // ------------------------------------------------------------ 工具定义

    private static final String[] TOOL_NAMES = {
            "storage_overview", "list_dir", "read_file", "read_files", "search_files",
            "set_note", "set_notes", "set_level", "set_favorite",
            "create_folder", "rename_path", "delete_path", "annotate_guide"
    };

    private static final String GUIDE =
            "这是「存储星图」的本地存储接口，根目录是手机内部存储。\n"
            + "给文件写中文备注前：先 list_dir 看上下文（大目录用 offset 翻页，或 unannotated=true 只看没备注的），"
            + "再对需要判断的文件调用 read_file / read_files 读内容，最后用 set_notes 批量写入。\n"
            + "storage_overview 的 deep 块会给出全盘已备注 / 未备注数量，方便知道还剩多少。\n"
            + "备注要求：6~14 个汉字的名词短语，说明「这是什么/装什么」，不要书名号、不写标点、"
            + "不复述文件名；拿不准时用「疑似」开头。不要改文件名，不要删文件，除非用户明确要求。";

    private JSONArray toolDefs() throws Exception {
        JSONArray arr = new JSONArray();
        // 总览：可选 deep 扫描全盘，给出已备注 / 未备注数量
        arr.put(tool("storage_overview", "总览：存储根、条目数、六档重要度计数、备注数；deep=true（默认）附全盘已备注/未备注统计",
                sch(new String[]{},
                        new String[]{"deep", "boolean", "是否扫描全盘统计未备注数量，默认 true；盘很大时可传 false 提速"})));
        arr.put(tool("list_dir", "列出目录内容（含中文备注、重要度、体积、修改时间）；支持 offset 翻页和只看未备注",
                sch(new String[]{"path"},
                        new String[]{"path", "string", "目录绝对路径，例如 /storage/emulated/0/Download；留空表示根目录"},
                        new String[]{"limit", "integer", "每页最多返回多少条，默认 200，上限 1000"},
                        new String[]{"offset", "integer", "跳过多少条，用于翻页，默认 0"},
                        new String[]{"sort", "string", "排序：name（默认）/ mtime（最新在前）/ size（最大在前）"},
                        new String[]{"unannotated", "boolean", "true 时只返回没有任何中文备注的条目"},
                        new String[]{"include_hidden", "boolean", "是否包含以 . 开头的隐藏项，默认 true"})));
        arr.put(tool("read_file", "读取文件内容用于理解它是什么：文本返回前 N 字节，其他返回元数据",
                sch(new String[]{"path"},
                        new String[]{"path", "string", "文件绝对路径"},
                        new String[]{"max_bytes", "integer", "文本最多读多少字节，默认 4096，上限 65536"},
                        new String[]{"mode", "string", "text（默认）或 meta"})));
        arr.put(tool("read_files", "批量读取多个文件的开头，用于快速判断一批文件是什么（最多 20 个）",
                sch(new String[]{"paths"},
                        new String[]{"paths", "array", "文件绝对路径数组，最多 20 个"},
                        new String[]{"max_bytes", "integer", "每个文件最多读多少字节，默认 1200，上限 8192"},
                        new String[]{"mode", "string", "text（默认）或 meta"})));
        arr.put(tool("search_files", "按文件名、中文备注或相对路径搜索，可按目录、扩展名、重要度过滤",
                sch(new String[]{"query"},
                        new String[]{"query", "string", "关键词"},
                        new String[]{"under", "string", "只在某个目录下搜索，留空表示整个存储"},
                        new String[]{"ext", "string", "扩展名过滤，例如 apk 或 png,jpg（不含点）"},
                        new String[]{"level", "integer", "只返回指定重要度 0-5 的条目"},
                        new String[]{"limit", "integer", "默认 40，上限 200"})));
        arr.put(tool("set_note", "给单个路径写中文备注（传空字符串清除备注）",
                sch(new String[]{"path", "note"},
                        new String[]{"path", "string", "绝对路径"},
                        new String[]{"note", "string", "中文备注"})));
        arr.put(tool("set_notes", "批量写备注，一次最多 200 条；返回新增/覆盖/清除/未变数量并检查备注是否合规",
                setNotesSchema()));
        arr.put(tool("set_level", "设置重要度：0 系统 1 珍贵 2 项目 3 普通 4 可清 5 空置；支持单条或批量",
                setLevelSchema()));
        arr.put(tool("set_favorite", "收藏或取消收藏",
                sch(new String[]{"path"},
                        new String[]{"path", "string", "绝对路径"},
                        new String[]{"on", "boolean", "true 收藏 / false 取消，默认 true"})));
        arr.put(tool("create_folder", "新建文件夹",
                sch(new String[]{"path"},
                        new String[]{"path", "string", "要创建的绝对路径"})));
        arr.put(tool("rename_path", "重命名文件或文件夹（只改最后一段名字）",
                sch(new String[]{"path", "new_name"},
                        new String[]{"path", "string", "原路径"},
                        new String[]{"new_name", "string", "新名字，不含 /"})));
        arr.put(tool("delete_path", "删除文件或文件夹（需用户在 App 面板里打开「允许删除」）",
                sch(new String[]{"path"},
                        new String[]{"path", "string", "绝对路径"},
                        new String[]{"confirm", "boolean", "必须为 true"})));
        arr.put(tool("annotate_guide", "获取「怎样写出准确的中文备注」的完整规则",
                sch(new String[]{})));
        return arr;
    }

    private static JSONObject setNotesSchema() throws Exception {
        JSONObject s = new JSONObject();
        s.put("type", "object");
        JSONObject props = new JSONObject();
        JSONObject items = new JSONObject();
        items.put("type", "array");
        items.put("description", "要写备注的条目，一次最多 200 条");
        JSONObject item = new JSONObject();
        item.put("type", "object");
        JSONObject ip = new JSONObject();
        ip.put("path", prop("string", "绝对路径"));
        ip.put("note", prop("string", "中文备注；空字符串表示清除该备注"));
        item.put("properties", ip);
        item.put("required", new JSONArray().put("path").put("note"));
        items.put("items", item);
        props.put("items", items);
        s.put("properties", props);
        s.put("required", new JSONArray().put("items"));
        return s;
    }

    private static JSONObject setLevelSchema() throws Exception {
        JSONObject s = new JSONObject();
        s.put("type", "object");
        JSONObject props = new JSONObject();
        props.put("path", prop("string", "单个路径；批量时改用 items"));
        props.put("level", prop("integer", "重要度 0-5；配合 path 使用"));
        JSONObject items = new JSONObject();
        items.put("type", "array");
        items.put("description", "批量设置：[{\"path\":\"...\",\"level\":3}]");
        JSONObject item = new JSONObject();
        item.put("type", "object");
        JSONObject ip = new JSONObject();
        ip.put("path", prop("string", "绝对路径"));
        ip.put("level", prop("integer", "重要度 0-5"));
        item.put("properties", ip);
        item.put("required", new JSONArray().put("path").put("level"));
        items.put("items", item);
        props.put("items", items);
        s.put("properties", props);
        s.put("required", new JSONArray());
        return s;
    }

    private static JSONObject prop(String type, String desc) throws Exception {
        JSONObject d = new JSONObject();
        d.put("type", type);
        d.put("description", desc);
        return d;
    }

    private static JSONObject tool(String name, String desc, JSONObject schema) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("description", desc);
        o.put("inputSchema", schema);
        return o;
    }

    private static JSONObject sch(String[] required, String[]... fields) throws Exception {
        JSONObject s = new JSONObject();
        s.put("type", "object");
        JSONObject props = new JSONObject();
        for (String[] f : fields) {
            JSONObject d = new JSONObject();
            d.put("type", f[1]);
            d.put("description", f[2]);
            props.put(f[0], d);
        }
        s.put("properties", props);
        JSONArray r = new JSONArray();
        for (String x : required) r.put(x);
        s.put("required", r);
        return s;
    }

    // ------------------------------------------------------------ 工具实现

    private String callTool(String name, JSONObject a) throws Exception {
        if (name.equals("storage_overview")) return overview(a);
        if (name.equals("list_dir")) return listDir(a);
        if (name.equals("read_file")) return readFile(a);
        if (name.equals("read_files")) return readFiles(a);
        if (name.equals("search_files")) return search(a);
        if (name.equals("set_note")) return setNote(a);
        if (name.equals("set_notes")) return setNotes(a);
        if (name.equals("set_level")) return setLevel(a);
        if (name.equals("set_favorite")) return setFavorite(a);
        if (name.equals("create_folder")) return createFolder(a);
        if (name.equals("rename_path")) return renamePath(a);
        if (name.equals("delete_path")) return deletePath(a);
        if (name.equals("annotate_guide")) return annotateGuide();
        throw new Exception("没有这个工具：" + name);
    }

    private File resolve(String p) throws Exception {
        if (p == null) p = "";
        p = p.trim();
        if (p.isEmpty() || p.equals("/") || p.equals(Atlas.ROOT)) return new File(Atlas.ROOT);
        if (!p.startsWith("/")) p = Atlas.ROOT + "/" + p;
        File f = new File(p);
        String canon = f.getCanonicalPath();
        if (!canon.equals(Atlas.ROOT) && !canon.startsWith(Atlas.ROOT + "/")) {
            throw new Exception("只允许操作手机存储内的路径：" + p);
        }
        return new File(canon);
    }

    private JSONObject entryJson(File f) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", f.getName());
        o.put("path", f.getAbsolutePath());
        o.put("dir", f.isDirectory());
        int lv = atlas.levelFor(f);
        o.put("level", lv);
        o.put("levelName", lv >= 0 && lv < Palette.LEVEL_NAME.length ? Palette.LEVEL_NAME[lv] : "普通");
        String note = atlas.customNoteFor(f);
        if (note == null) note = atlas.noteFor(f);
        o.put("note", note == null ? "" : note);
        if (f.isDirectory()) {
            String[] kids = f.list();
            o.put("childCount", kids == null ? 0 : kids.length);
        } else {
            o.put("size", f.length());
        }
        o.put("mtime", f.lastModified());
        o.put("mtimeText", iso(f.lastModified()));
        return o;
    }

    private String overview(JSONObject a) throws Exception {
        File root = new File(Atlas.ROOT);
        int[] counts = new int[6];
        int dirs = 0, files = 0;
        String[] kids = root.list();
        if (kids != null) {
            for (String k : kids) {
                File f = new File(root, k);
                if (f.isDirectory()) dirs++; else files++;
                int lv = atlas.levelFor(f);
                if (lv >= 0 && lv < 6) counts[lv]++;
            }
        }
        JSONObject o = new JSONObject();
        o.put("root", Atlas.ROOT);
        o.put("dirs", dirs);
        o.put("files", files);
        JSONArray levels = new JSONArray();
        for (int i = 0; i < 6; i++) {
            JSONObject x = new JSONObject();
            x.put("level", i);
            x.put("name", Palette.LEVEL_NAME[i]);
            x.put("count", counts[i]);
            levels.put(x);
        }
        o.put("levels", levels);
        o.put("customNotes", atlas.customNoteCount());
        o.put("allowWrite", atlas.mcpAllowWrite);
        o.put("allowDelete", atlas.mcpAllowDelete);

        // 全盘扫描：知道还有多少条目没写备注，比逐个目录翻更省事。
        if (a.optBoolean("deep", true)) {
            long now = System.currentTimeMillis();
            JSONObject cached = deepCache;
            boolean fresh = cached != null && now - deepCacheAt < DEEP_TTL_MS;
            if (fresh) {
                o.put("deep", cached);
                o.put("deepCached", true);
            } else {
            final int cap = 40000;
            long t0 = System.currentTimeMillis();
            int scanned = 0, annotated = 0;
            ArrayList<File> queue = new ArrayList<>();
            ArrayList<Integer> depths = new ArrayList<>();
            queue.add(root);
            depths.add(0);
            int head = 0;
            boolean hitCap = false;
            while (head < queue.size()) {
                if (scanned >= cap) { hitCap = true; break; }
                File d = queue.get(head);
                int dep = depths.get(head);
                head++;
                if (dep > 40) continue;
                File[] ks = d.listFiles();
                if (ks == null) continue;
                for (File f : ks) {
                    if (scanned >= cap) { hitCap = true; break; }
                    scanned++;
                    if (!noteSafe(f).isEmpty()) annotated++;
                    if (f.isDirectory()) {
                        queue.add(f);
                        depths.add(dep + 1);
                    }
                }
            }
            JSONObject d = new JSONObject();
            d.put("scanned", scanned);
            d.put("annotated", annotated);
            d.put("unannotated", scanned - annotated);
            d.put("truncated", hitCap);
            d.put("cap", cap);
            d.put("elapsedMs", System.currentTimeMillis() - t0);
            d.put("rule", "备注为空（自定义/内置/词条都没有）才算未备注");
            deepCache = d;
            deepCacheAt = now;
            o.put("deep", d);
            o.put("deepCached", false);
            }
        }
        o.put("guide", "先看 deep.unannotated 知道还剩多少，再用 list_dir(unannotated=true) 定位，read_files 批量判断，set_notes 批量写入。");
        return o.toString(2);
    }

    private String listDir(JSONObject a) throws Exception {
        File dir = resolve(a.optString("path", ""));
        if (!dir.isDirectory()) throw new Exception("不是文件夹：" + dir.getAbsolutePath());
        int limit = a.optInt("limit", 200);
        if (limit <= 0) limit = 200;
        if (limit > 1000) limit = 1000;
        int offset = a.optInt("offset", 0);
        if (offset < 0) offset = 0;
        final String sortKey = a.optString("sort", "name").trim().toLowerCase(Locale.ROOT);
        boolean onlyUn = a.optBoolean("unannotated", false);
        boolean showHidden = a.optBoolean("include_hidden", true);

        File[] kids = dir.listFiles();
        ArrayList<File> list = new ArrayList<>();
        if (kids != null) {
            for (File f : kids) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                if (onlyUn && !noteSafe(f).isEmpty()) continue;
                list.add(f);
            }
        }
        Collections.sort(list, new java.util.Comparator<File>() {
            @Override public int compare(File x, File y) {
                if (sortKey.equals("mtime")) {
                    int c = Long.compare(y.lastModified(), x.lastModified());
                    return c != 0 ? c : x.getName().compareToIgnoreCase(y.getName());
                }
                if (sortKey.equals("size")) {
                    long sx = x.isFile() ? x.length() : 0;
                    long sy = y.isFile() ? y.length() : 0;
                    if (sx != sy) return Long.compare(sy, sx);
                    return x.getName().compareToIgnoreCase(y.getName());
                }
                if (x.isDirectory() != y.isDirectory()) return x.isDirectory() ? -1 : 1;
                return x.getName().compareToIgnoreCase(y.getName());
            }
        });
        int total = list.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        JSONArray arr = new JSONArray();
        for (int i = from; i < to; i++) arr.put(entryJson(list.get(i)));

        JSONObject o = new JSONObject();
        o.put("path", dir.getAbsolutePath());
        o.put("total", total);
        o.put("offset", offset);
        o.put("returned", arr.length());
        o.put("truncated", to < total);
        o.put("nextOffset", to < total ? to : JSONObject.NULL);
        o.put("items", arr);
        return o.toString(2);
    }

    private static final String[] TEXT_EXT = {"txt", "md", "markdown", "json", "xml", "html", "htm",
            "css", "js", "ts", "jsx", "tsx", "java", "kt", "py", "sh", "bat", "c", "h", "cpp", "cs",
            "go", "rs", "php", "rb", "sql", "yml", "yaml", "ini", "conf", "properties", "gradle",
            "csv", "log", "srt", "vtt", "toml", "gitignore", "editorconfig", "lua", "vue"};

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private String readFile(JSONObject a) throws Exception {
        File f = resolve(a.optString("path", ""));
        if (!f.isFile()) throw new Exception("不是文件：" + f.getAbsolutePath());
        int max = a.optInt("max_bytes", 4096);
        if (max <= 0) max = 4096;
        if (max > 65536) max = 65536;
        return readOne(f, max, a.optString("mode", "text"), true).toString(2);
    }

    /** 批量读取：一次判断一堆文件是什么，省掉 N 次往返。 */
    private String readFiles(JSONObject a) throws Exception {
        JSONArray paths = a.optJSONArray("paths");
        if (paths == null || paths.length() == 0) throw new Exception("paths 不能为空");
        if (paths.length() > 20) throw new Exception("一次最多读 20 个文件");
        int max = a.optInt("max_bytes", 1200);
        if (max <= 0) max = 1200;
        if (max > 8192) max = 8192;
        String mode = a.optString("mode", "text");
        JSONArray out = new JSONArray();
        for (int i = 0; i < paths.length(); i++) {
            String path = paths.optString(i, "");
            try {
                File f = resolve(path);
                if (!f.isFile()) throw new Exception("不是文件");
                out.put(readOne(f, max, mode, false));
            } catch (Exception e) {
                JSONObject bad = new JSONObject();
                bad.put("path", path);
                bad.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                out.put(bad);
            }
        }
        JSONObject o = new JSONObject();
        o.put("count", out.length());
        o.put("items", out);
        return o.toString(2);
    }

    private JSONObject readOne(File f, int max, String mode, boolean withSiblings) throws Exception {
        String e = ext(f.getName());
        JSONObject o = new JSONObject();
        o.put("path", f.getAbsolutePath());
        o.put("size", f.length());
        o.put("ext", e);
        o.put("note", noteSafe(f));
        o.put("mtime", f.lastModified());
        o.put("mtimeText", iso(f.lastModified()));

        boolean text = false;
        for (String t : TEXT_EXT) if (t.equals(e)) { text = true; break; }
        if (!text && f.length() < 256 * 1024) {
            text = looksLikeText(f, Math.min(4096, (int) f.length()));
        }
        if (!mode.equals("meta") && text) {
            o.put("kind", "text");
            o.put("encoding", "UTF-8（尽量）");
            o.put("truncated", f.length() > max);
            o.put("content", readText(f, max));
        } else {
            o.put("kind", "binary");
            o.put("content", "");
            o.put("hint", "二进制文件，根据扩展名和所在目录推断用途");
        }
        // 同目录下的兄弟文件，帮助判断用途
        if (withSiblings) {
            File parent = f.getParentFile();
            if (parent != null) {
                String[] kids = parent.list();
                if (kids != null) {
                    JSONArray sib = new JSONArray();
                    int c = 0;
                    for (String k : kids) {
                        if (k.equals(f.getName())) continue;
                        if (c++ >= 8) break;
                        sib.put(k);
                    }
                    o.put("siblings", sib);
                }
            }
        }
        return o;
    }

    private static String iso(long ms) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
            f.setTimeZone(TimeZone.getDefault());
            return f.format(new Date(ms));
        } catch (Exception e) {
            return "";
        }
    }

    private String noteSafe(File f) {
        String n = atlas.customNoteFor(f);
        if (n == null) n = atlas.noteFor(f);
        return n == null ? "" : n;
    }

    private static boolean looksLikeText(File f, int len) {
        if (len <= 0) return true;
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[len];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return true;
            int bad = 0;
            for (int i = 0; i < n; i++) {
                int b = buf[i] & 0xff;
                if (b == 0) return false;
                if (b < 9 || (b > 13 && b < 32)) bad++;
            }
            return bad * 100 / n < 3;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readText(File f, int max) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(max, f.length())];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String search(JSONObject a) throws Exception {
        final String q = a.optString("query", "").trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) throw new Exception("query 不能为空");
        int limit = a.optInt("limit", 40);
        if (limit <= 0) limit = 40;
        if (limit > 200) limit = 200;
        String under = a.optString("under", "").trim();
        File base = under.isEmpty() ? new File(Atlas.ROOT) : resolve(under);
        if (!base.isDirectory()) throw new Exception("under 不是文件夹：" + base.getAbsolutePath());
        int level = a.optInt("level", -1);
        if (level < -1 || level > 5) throw new Exception("level 必须是 0-5");

        ArrayList<String> extList = new ArrayList<>();
        String extStr = a.optString("ext", "").trim().toLowerCase(Locale.ROOT);
        if (!extStr.isEmpty()) {
            for (String s : extStr.split(",")) {
                s = s.trim();
                if (s.startsWith(".")) s = s.substring(1);
                if (!s.isEmpty()) extList.add(s);
            }
        }

        final int cap = 40000;
        int scanned = 0;
        boolean hitCap = false;
        JSONArray arr = new JSONArray();
        ArrayList<File> queue = new ArrayList<>();
        queue.add(base);
        int head = 0;
        while (head < queue.size() && arr.length() < limit) {
            File dir = queue.get(head++);
            File[] kids = dir.listFiles();
            if (kids == null) continue;
            for (File f : kids) {
                if (scanned >= cap) { hitCap = true; break; }
                scanned++;
                boolean isDir = f.isDirectory();
                if (!extList.isEmpty() && (isDir || !extList.contains(ext(f.getName())))) {
                    if (!isDir) continue;
                }
                if (level >= 0 && atlas.levelFor(f) != level) {
                    if (isDir && !f.getName().startsWith(".")) queue.add(f);
                    continue;
                }
                String name = f.getName().toLowerCase(Locale.ROOT);
                String note = noteSafe(f).toLowerCase(Locale.ROOT);
                String rel = Atlas.rel(f.getAbsolutePath()).toLowerCase(Locale.ROOT);
                if (name.contains(q) || note.contains(q) || rel.contains(q)) {
                    JSONObject o = entryJson(f);
                    o.put("parent", dir.getAbsolutePath());
                    arr.put(o);
                    if (arr.length() >= limit) break;
                }
                if (isDir && !f.getName().startsWith(".")) queue.add(f);
            }
        }
        JSONObject o = new JSONObject();
        o.put("query", q);
        o.put("under", base.getAbsolutePath());
        o.put("scanned", scanned);
        o.put("scanCap", cap);
        o.put("hitCap", hitCap);
        o.put("count", arr.length());
        o.put("items", arr);
        return o.toString(2);
    }

    private String setNote(JSONObject a) throws Exception {
        if (!atlas.mcpAllowWrite) throw new Exception("用户在 App 里关闭了「允许 AI 修改」");
        File f = resolve(a.optString("path", ""));
        if (!f.exists()) throw new Exception("路径不存在：" + f.getAbsolutePath());
        String note = a.optString("note", "").trim();
        String old = atlas.customNoteFor(f);
        atlas.setCustomNote(f, note);
        if ((old == null) != note.isEmpty()) deepAdjust(old == null ? 1 : -1);
        changed();
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("path", f.getAbsolutePath());
        o.put("note", noteSafe(f));
        return o.toString(2);
    }

    private String setNotes(JSONObject a) throws Exception {
        if (!atlas.mcpAllowWrite) throw new Exception("用户在 App 里关闭了「允许 AI 修改」");
        JSONArray items = a.optJSONArray("items");
        if (items == null) throw new Exception("items 必须是数组");
        if (items.length() > 200) throw new Exception("一次最多 200 条");
        int created = 0, updated = 0, cleared = 0, unchanged = 0, deepDelta = 0;
        JSONArray failed = new JSONArray();
        JSONArray warnings = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) {
                JSONObject bad = new JSONObject();
                bad.put("path", "");
                bad.put("error", "第 " + (i + 1) + " 条不是对象");
                failed.put(bad);
                continue;
            }
            String path = it.optString("path", "");
            try {
                File f = resolve(path);
                if (!f.exists()) throw new Exception("不存在");
                String note = it.optString("note", "").trim();
                String old = atlas.customNoteFor(f);
                String warn = note.isEmpty() ? null : noteWarning(f, note);
                if (warn != null) {
                    JSONObject w = new JSONObject();
                    w.put("path", f.getAbsolutePath());
                    w.put("warning", warn);
                    warnings.put(w);
                }
                if (note.isEmpty()) {
                    if (old == null) unchanged++;
                    else { atlas.setCustomNote(f, ""); cleared++; deepDelta--; }
                } else if (note.equals(old)) {
                    unchanged++;
                } else if (old == null) {
                    atlas.setCustomNote(f, note);
                    created++;
                    deepDelta++;
                } else {
                    atlas.setCustomNote(f, note);
                    updated++;
                }
            } catch (Exception e) {
                JSONObject bad = new JSONObject();
                bad.put("path", path);
                bad.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                failed.put(bad);
            }
        }
        if (deepDelta != 0) deepAdjust(deepDelta);
        changed();
        JSONObject o = new JSONObject();
        o.put("submitted", items.length());
        o.put("ok", items.length() - failed.length());
        o.put("created", created);
        o.put("updated", updated);
        o.put("cleared", cleared);
        o.put("unchanged", unchanged);
        o.put("failed", failed);
        o.put("warnings", warnings);
        o.put("totalNoted", atlas.customNoteCount());
        return o.toString(2);
    }

    /** 备注是否合乎规范：别太长太短、别用标点、别照抄文件名。 */
    private static String noteWarning(File f, String note) {
        int len = note.codePointCount(0, note.length());
        if (len < 4) return "太短，建议 6~14 个汉字";
        if (len > 18) return "偏长（" + len + " 字），建议 6~14 个汉字";
        String punct = "。，、；：！？（）【】《》“”‘’";
        for (int i = 0; i < punct.length(); i++) {
            if (note.indexOf(punct.charAt(i)) >= 0) return "不要用标点";
        }
        if (note.equals(f.getName())) return "不要照抄文件名";
        return null;
    }

    private String setLevel(JSONObject a) throws Exception {
        if (!atlas.mcpAllowWrite) throw new Exception("用户在 App 里关闭了「允许 AI 修改」");
        JSONArray failed = new JSONArray();
        JSONArray results = new JSONArray();
        JSONArray items = a.optJSONArray("items");
        if (items != null) {
            if (items.length() > 200) throw new Exception("一次最多 200 条");
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                String path = it.optString("path", "");
                try {
                    applyLevel(resolve(path), it.optInt("level", -1), results);
                } catch (Exception e) {
                    JSONObject bad = new JSONObject();
                    bad.put("path", path);
                    bad.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    failed.put(bad);
                }
            }
        } else {
            File f = resolve(a.optString("path", ""));
            if (!f.exists()) throw new Exception("路径不存在：" + f.getAbsolutePath());
            applyLevel(f, a.optInt("level", -1), results);
        }
        changed();
        JSONObject o = new JSONObject();
        o.put("ok", results.length());
        o.put("updated", results.length());
        o.put("failed", failed);
        o.put("items", results);
        if (results.length() == 1) {
            JSONObject only = results.optJSONObject(0);
            if (only != null) {
                o.put("path", only.optString("path"));
                o.put("level", only.optInt("level"));
                o.put("levelName", only.optString("levelName"));
            }
        }
        return o.toString(2);
    }

    private void applyLevel(File f, int lv, JSONArray results) throws Exception {
        if (!f.exists()) throw new Exception("不存在");
        if (lv < 0 || lv > 5) throw new Exception("level 必须是 0-5");
        atlas.setLevelOverride(f, lv);
        JSONObject x = new JSONObject();
        x.put("path", f.getAbsolutePath());
        x.put("level", lv);
        x.put("levelName", Palette.LEVEL_NAME[lv]);
        results.put(x);
    }

    private String setFavorite(JSONObject a) throws Exception {
        File f = resolve(a.optString("path", ""));
        boolean on = a.optBoolean("on", true);
        boolean cur = atlas.isFavorite(f);
        if (on != cur) atlas.toggleFavorite(f);
        changed();
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("path", f.getAbsolutePath());
        o.put("favorite", atlas.isFavorite(f));
        return o.toString(2);
    }

    private String createFolder(JSONObject a) throws Exception {
        if (!atlas.mcpAllowWrite) throw new Exception("用户在 App 里关闭了「允许 AI 修改」");
        File f = resolve(a.optString("path", ""));
        if (f.exists()) throw new Exception("已存在：" + f.getAbsolutePath());
        boolean ok = f.mkdirs();
        if (!ok) throw new Exception("新建文件夹失败（无权限或已存在）：" + f.getAbsolutePath());
        deepCache = null;
        changed();
        JSONObject o = new JSONObject();
        o.put("ok", ok);
        o.put("path", f.getAbsolutePath());
        return o.toString(2);
    }

    private String renamePath(JSONObject a) throws Exception {
        if (!atlas.mcpAllowWrite) throw new Exception("用户在 App 里关闭了「允许 AI 修改」");
        File f = resolve(a.optString("path", ""));
        if (!f.exists()) throw new Exception("路径不存在：" + f.getAbsolutePath());
        String name = a.optString("new_name", "").trim();
        if (name.isEmpty() || name.contains("/")) throw new Exception("new_name 不能为空且不能包含 /");
        File to = new File(f.getParentFile(), name);
        if (to.exists()) throw new Exception("目标已存在：" + to.getAbsolutePath());
        boolean ok = f.renameTo(to);
        if (!ok) throw new Exception("重命名失败（无权限或文件被占用）：" + f.getAbsolutePath());
        atlas.dropCache(f.getAbsolutePath());
        deepCache = null;
        changed();
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("from", f.getAbsolutePath());
        o.put("to", to.getAbsolutePath());
        return o.toString(2);
    }

    private String deletePath(JSONObject a) throws Exception {
        if (!atlas.mcpAllowDelete) throw new Exception("删除被禁用：请用户在 App 的 AI 面板里打开「允许删除」");
        if (!a.optBoolean("confirm", false)) throw new Exception("需要 confirm=true 才执行删除");
        File f = resolve(a.optString("path", ""));
        if (f.getAbsolutePath().equals(Atlas.ROOT)) throw new Exception("不能删除存储根目录");
        boolean ok = MainActivity.deleteRecursive(f);
        atlas.dropCache(f.getAbsolutePath());
        deepCache = null;
        changed();
        JSONObject o = new JSONObject();
        o.put("ok", ok);
        o.put("deleted", f.getAbsolutePath());
        return o.toString(2);
    }

    private String annotateGuide() throws Exception {
        JSONObject o = new JSONObject();
        o.put("目标", "给文件/文件夹写一眼能看懂的中文名，让用户不用打开就知道它是什么、能不能删。");
        JSONArray steps = new JSONArray();
        steps.put("1. storage_overview 先看 deep.unannotated，知道全盘还剩多少没备注");
        steps.put("2. list_dir 看这一层的语境；大目录用 offset 翻页，unannotated=true 只看没备注的");
        steps.put("3. 对不确定的文件用 read_files 一次读多个的开头，比逐个 read_file 省往返");
        steps.put("4. set_notes 批量写（一次 200 条），返回值会区分新增/覆盖/清除，并列出不合规的备注");
        o.put("流程", steps);
        JSONArray rules = new JSONArray();
        rules.put("6~14 个汉字的名词短语：『微信下载的表情包』而不是『这是一个表情包文件』");
        rules.put("不复述文件名，不写书名号、句号、括号注释");
        rules.put("区分来源：微信 / QQ / 浏览器 / 某 App 缓存 / 系统");
        rules.put("看不出内容时用『疑似…』开头，别编造");
        rules.put("缓存类写明『缓存（可清理）』；安装包写『XX 安装包』");
        rules.put("不改文件名、不删文件、不移动文件，除非用户明确要求");
        rules.put("失败/无权限就跳过，不要反复重试同一路径");
        o.put("规则", rules);
        o.put("示例", new JSONArray()
                .put("Download/netease/cloudmusic/Music → 网易云音乐下载的歌曲")
                .put("Download/xxx.apk → 某 App 安装包")
                .put("Pictures/WeiXin/mmexport1699.jpg → 微信保存的图片"));
        return o.toString(2);
    }
}