package com.dshgo.storageatlas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 数据仓库：内置中文词典、重要度规则、用户备注与收藏。 */
public final class Atlas {

    private static Atlas INSTANCE;

    public static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static final Map<String, String> LEVEL_TOP = new HashMap<>();

    static {
        String[][] m = {
                // 只保留通用目录：任何一台 Android 手机都可能出现
                {"Android", "S"}, {"ColorOS", "S"}, {"MIUI", "S"}, {"Huawei", "S"},
                {"OPPO", "S"}, {"vivo", "S"}, {"Xiaomi", "S"}, {"Samsung", "S"},
                {"realme", "S"}, {"OnePlus", "S"},
                {"DCIM", "A"}, {"Documents", "A"}, {"Pictures", "A"}, {"Music", "A"},
                {"Movies", "A"}, {"Tencent", "A"}, {"Recordings", "A"}, {"MoBiMusic", "A"},
                {"WhatsApp", "A"}, {"Telegram", "A"}, {"BaiduNetdisk", "A"}, {"netease", "A"},
                {"Alarms", "C"}, {"Download", "C"}, {"下载", "C"}, {"backups", "C"},
                {"UCDownloads", "C"}, {"QuarkCloudDrive", "C"}, {"ByteDownload", "C"},
                {"Browser", "D"},
                {"Audiobooks", "E"}, {"Fonts", "E"}, {"log", "E"}, {"My Documents", "E"},
                {"Notifications", "E"}, {"Podcasts", "E"}, {"Ringtones", "E"},
        };
        for (String[] p : m) LEVEL_TOP.put(p[0], p[1]);
    }

    private static final Set<String> CACHE_SEG = new HashSet<>();
    static {
        String[] c = {"cache", ".cache", "code_cache", ".thumbnails", "thumbnails", "thumb",
                "debug", "tmp", "temp", ".tmp", "node_modules", "build", "out", "logs", "log",
                "dexopt", "merged.dir", "stripped.dir", "dexBuilderDebug",
                "mergeDebugResources", "mergeDebugAssets", "mergeDebugShaders",
                "mergeDebugJniLibFolders", "processDebugResources", "processDebugMainManifest",
                "packageDebugResources", "desugarDebugFileDependencies", ".recycle.bin"};
        for (String s : c) CACHE_SEG.add(s);
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final HashMap<String, String> builtinNotes = new HashMap<>();
    private final HashMap<String, Integer> builtinLevels = new HashMap<>();
    private final HashMap<String, String> glossary = new HashMap<>();
    private final HashMap<String, String> customNotes = new HashMap<>();
    private final HashSet<String> favorites = new HashSet<>();
    private final HashMap<String, Long> sizeCache = new HashMap<>();
    private final HashMap<String, Integer> levelOverrides = new HashMap<>();

    public boolean scanSizes = true;

    /** 排序：0 重要度（默认，同档聚在一起）1 名称 2 大小 3 时间 4 类型 */
    public int sortMode = 0;
    public boolean sortDesc = false;
    /** 主标题：默认原名优先，备注退到副标题 */
    public boolean noteFirst = false;

    // ---- AI（MCP）连接设置 ----
    public boolean mcpEnabled = false;
    public String mcpPort = "8765";
    public String mcpToken = "";
    public boolean mcpLan = false;
    public boolean mcpAllowWrite = true;
    public boolean mcpAllowDelete = false;

    public static final String[] SORT_NAMES = {"重要度", "名称", "大小", "时间", "类型"};
    public static final String[] SORT_HINTS = {
            "同档位聚在一起（默认）", "按显示名排序", "体积大的在前",
            "最近修改的在前", "按扩展名归类"};

    /** 切换排序方式时的默认方向：大小、时间默认从大到小 / 从新到旧。 */
    public static boolean defaultDesc(int mode) { return mode == 2 || mode == 3; }

    private Atlas(Context c) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE);
        loadBuiltin();
        loadUser();
    }

    public static synchronized Atlas get(Context c) {
        if (INSTANCE == null) INSTANCE = new Atlas(c);
        return INSTANCE;
    }

    private void loadBuiltin() {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    ctx.getAssets().open("builtin.json"), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONObject p = root.optJSONObject("paths");
            if (p != null) {
                for (java.util.Iterator<String> it = p.keys(); it.hasNext(); ) {
                    String k = it.next();
                    builtinNotes.put(k, p.optString(k));
                }
            }
            JSONObject l = root.optJSONObject("levels");
            if (l != null) {
                for (java.util.Iterator<String> it = l.keys(); it.hasNext(); ) {
                    String k = it.next();
                    int idx = indexOfCode(l.optString(k));
                    if (idx >= 0) builtinLevels.put(k, idx);
                }
            }
            JSONObject g = root.optJSONObject("glossary");
            if (g != null) {
                for (java.util.Iterator<String> it = g.keys(); it.hasNext(); ) {
                    String k = it.next();
                    glossary.put(k, g.optString(k));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int indexOfCode(String code) {
        for (int i = 0; i < Palette.LEVEL_CODE.length; i++) {
            if (Palette.LEVEL_CODE[i].equals(code)) return i;
        }
        return -1;
    }

    private void loadUser() {
        try {
            JSONObject o = new JSONObject(prefs.getString("custom_notes", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                customNotes.put(k, o.optString(k));
            }
        } catch (Exception ignored) {
        }
        for (String s : prefs.getStringSet("favorites", new HashSet<>())) favorites.add(s);
        try {
            JSONObject o = new JSONObject(prefs.getString("level_overrides", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                levelOverrides.put(k, o.optInt(k, 3));
            }
        } catch (Exception ignored) {
        }
        scanSizes = prefs.getBoolean("scan_sizes", true);
        sortMode = prefs.getInt("sort_mode", 0);
        sortDesc = prefs.getBoolean("sort_desc", false);
        noteFirst = prefs.getBoolean("note_first", false);
        mcpEnabled = prefs.getBoolean("mcp_enabled", false);
        mcpPort = prefs.getString("mcp_port", "8765");
        mcpToken = prefs.getString("mcp_token", "");
        mcpLan = prefs.getBoolean("mcp_lan", false);
        mcpAllowWrite = prefs.getBoolean("mcp_allow_write", true);
        mcpAllowDelete = prefs.getBoolean("mcp_allow_delete", false);
        if (mcpToken == null || mcpToken.isEmpty()) {
            mcpToken = newToken();
            prefs.edit().putString("mcp_token", mcpToken).apply();
        }
    }

    /** 生成一串联机令牌：本机 127.0.0.1 之外的请求必须带它。 */
    public static String newToken() {
        String hex = "0123456789abcdef";
        StringBuilder sb = new StringBuilder();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 24; i++) sb.append(hex.charAt(r.nextInt(16)));
        return sb.toString();
    }

    public void setMcp(String key, Object value) {
        android.content.SharedPreferences.Editor e = prefs.edit();
        if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else e.putString(key, String.valueOf(value));
        e.apply();
        if ("mcp_enabled".equals(key)) mcpEnabled = (Boolean) value;
        else if ("mcp_port".equals(key)) mcpPort = String.valueOf(value);
        else if ("mcp_token".equals(key)) mcpToken = String.valueOf(value);
        else if ("mcp_lan".equals(key)) mcpLan = (Boolean) value;
        else if ("mcp_allow_write".equals(key)) mcpAllowWrite = (Boolean) value;
        else if ("mcp_allow_delete".equals(key)) mcpAllowDelete = (Boolean) value;
    }

    public void setSort(int mode, boolean desc) {
        sortMode = mode;
        sortDesc = desc;
        prefs.edit().putInt("sort_mode", mode).putBoolean("sort_desc", desc).apply();
    }

    public void setNoteFirst(boolean v) {
        noteFirst = v;
        prefs.edit().putBoolean("note_first", v).apply();
    }

    public void saveCustomNotes() {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String> e : customNotes.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("custom_notes", o.toString()).apply();
    }

    public void saveFavorites() {
        prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
    }

    public void setScanSizes(boolean v) {
        scanSizes = v;
        prefs.edit().putBoolean("scan_sizes", v).apply();
    }

    public int builtinCount() { return builtinNotes.size(); }

    public static String rel(String absPath) {
        if (absPath == null) return "";
        if (absPath.equals(ROOT)) return "";
        if (absPath.startsWith(ROOT + "/")) return absPath.substring(ROOT.length() + 1);
        return absPath;
    }

    public static String abs(String relPath) {
        if (relPath == null || relPath.isEmpty()) return ROOT;
        return ROOT + "/" + relPath;
    }

    /** 中文备注：自定义 > 内置路径 > 通用词。 */
    public String noteFor(File f) {
        String rel = rel(f.getAbsolutePath());
        String custom = customNotes.get(rel);
        if (custom != null) return custom;
        String builtin = builtinNotes.get(rel);
        if (builtin != null && !builtin.isEmpty()) return builtin;
        return glossary.get(f.getName());
    }

    public String customNoteFor(File f) {
        return customNotes.get(rel(f.getAbsolutePath()));
    }

    public void setCustomNote(File f, String note) {
        String rel = rel(f.getAbsolutePath());
        if (note == null || note.trim().isEmpty()) customNotes.remove(rel);
        else customNotes.put(rel, note.trim());
        saveCustomNotes();
    }

    public void clearCustomNotes() {
        customNotes.clear();
        saveCustomNotes();
    }

    public int customNoteCount() { return customNotes.size(); }

    public boolean isFavorite(File f) {
        return favorites.contains(f.getAbsolutePath());
    }

    public void toggleFavorite(File f) {
        String p = f.getAbsolutePath();
        if (!favorites.remove(p)) favorites.add(p);
        saveFavorites();
    }

    public List<File> favorites() {
        List<File> out = new ArrayList<>();
        for (String p : favorites) out.add(new File(p));
        return out;
    }

    /** 重要度 0..5，规则：空目录 > 缓存特征 > 内置等级 > 顶层映射 > 默认。 */
    public void setLevelOverride(File f, int level) {
        levelOverrides.put(rel(f.getAbsolutePath()), level);
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Integer> e : levelOverrides.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("level_overrides", o.toString()).apply();
    }

    public int levelOverride(File f) {
        Integer v = levelOverrides.get(rel(f.getAbsolutePath()));
        return v == null ? -1 : v;
    }

    public int levelFor(File f) {
        boolean dir = f.isDirectory();
        String rel = rel(f.getAbsolutePath());
        Integer over = levelOverrides.get(rel);
        if (over != null) return over;
        if (dir) {
            String[] kids = f.list();
            if (kids != null && kids.length == 0) return 5;
            String custom = customNotes.get(rel);
            if (custom == null) {
                Integer lv = builtinLevels.get(rel);
                if (lv != null) return lv;
            }
        }
        String[] parts = rel.split("/");
        String base = f.getName();
        String low = base.toLowerCase();
        if (!dir) {
            if (low.startsWith(".trashed") || low.endsWith(".log")) return 4;
            if (low.endsWith(".apk") || low.endsWith(".xapk") || low.endsWith(".apkm")) return 3;
        }
        for (String seg : parts) {
            String s = seg.toLowerCase();
            if (CACHE_SEG.contains(s) || s.startsWith("mergedebug") || s.startsWith("processdebug")
                    || s.startsWith("packagedebug") || s.startsWith("compiledebug")
                    || s.startsWith("desugardebug") || s.startsWith("dexbuilder")) {
                return 4;
            }
        }
        if (rel.startsWith("Android/") || rel.startsWith("ColorOS/")) return 0;
        String top = parts.length > 0 ? parts[0] : "";
        String lv = LEVEL_TOP.get(top);
        if (lv != null) return indexOfCode(lv);
        return 3;
    }

    public Long cachedSize(String path) { return sizeCache.get(path); }

    public void dropCache(String prefix) {
        java.util.Iterator<String> it = sizeCache.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals(prefix) || k.startsWith(prefix + "/")) it.remove();
        }
    }

    public void putSize(String path, long size) { sizeCache.put(path, size); }

    public long totalSizeOf(File f) {
        Long c = sizeCache.get(f.getAbsolutePath());
        if (c != null) return c;
        long[] n = new long[]{0};
        long size = walk(f, n, 0);
        sizeCache.put(f.getAbsolutePath(), size);
        return size;
    }

    private long walk(File f, long[] count, int depth) {
        if (depth > 24) return 0;
        if (f.isFile()) {
            count[0]++;
            return f.length();
        }
        File[] kids = f.listFiles();
        if (kids == null) return 0;
        long sum = 0;
        for (File k : kids) {
            if (k.isFile()) { sum += k.length(); count[0]++; }
            else sum += walk(k, count, depth + 1);
        }
        return sum;
    }

    public long countChildren(File f) {
        File[] kids = f.listFiles();
        return kids == null ? 0 : kids.length;
    }

    public Context ctx() { return ctx; }
}
