package com.dshgo.storageatlas;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 当前目录下的树：显示子项，展开后原地嵌套；筛选在可见树上生效。 */
public class TreeAdapter extends BaseAdapter {

    public interface SizeListener { void onNeedSize(FsNode n); }

    private final Context c;
    private final List<FsNode> visible = new ArrayList<>();
    public FsNode dirRoot;
    public final FsNode fsRoot;
    public int filterLevel = -1;
    public SizeListener sizeListener;
    public Runnable onChanged;

    public TreeAdapter(Context c, FsNode root) {
        this.c = c;
        this.fsRoot = root;
        this.dirRoot = root;
        rebuild();
    }

    public void setDirRoot(FsNode node) {
        dirRoot = node;
        rebuild();
    }

    public void rebuild() {
        visible.clear();
        if (dirRoot != null) {
            dirRoot.load(Atlas.get(c));
            addChildren(dirRoot);
        }
        notifyDataSetChanged();
    }

    private void addChildren(FsNode parent) {
        if (parent.children == null) return;
        Atlas atlas = Atlas.get(c);
        ArrayList<FsNode> shown = new ArrayList<>();
        for (FsNode n : parent.children) {
            if (n.level < 0) n.level = atlas.levelFor(n.file);
            if (filterLevel >= 0 && n.level != filterLevel) {
                if (n.dir && n.expanded) addChildren(n);
                continue;
            }
            shown.add(n);
        }
        if (shown.size() > 1) Collections.sort(shown, comparator(atlas));
        for (int i = 0; i < shown.size(); i++) {
            FsNode n = shown.get(i);
            n.lastChild = (i == shown.size() - 1);
            visible.add(n);
            if (n.dir && n.expanded) addChildren(n);
        }
    }

    /** 显示名：按设置决定备注还是原名当主标题，排序也用它。 */
    private static String title(FsNode n, Atlas atlas) {
        String note = n.display(atlas);
        boolean has = note != null && !note.isEmpty() && !note.equals(n.name);
        return atlas.noteFirst && has ? note : n.name;
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private static String typeKey(FsNode n) {
        if (n.dir) return "0";  // 文件夹优先
        String name = n.name;
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "1";
        return "1" + name.substring(i + 1).toUpperCase(Locale.ROOT);
    }

    private static long sizeAsc(FsNode n) {
        long v = n.dir ? n.size : n.file.length();
        return v < 0 ? Long.MAX_VALUE : v;
    }

    private static long sizeDesc(FsNode n) {
        long v = n.dir ? n.size : n.file.length();
        return v < 0 ? -1 : v;
    }

    private Comparator<FsNode> comparator(final Atlas a) {
        Comparator<FsNode> cmp;
        switch (a.sortMode) {
            case 1: // 名称
                cmp = (x, y) -> lower(title(x, a)).compareTo(lower(title(y, a)));
                if (a.sortDesc) cmp = cmp.reversed();
                break;
            case 2: // 大小（未知体积始终沉底）
                cmp = a.sortDesc
                        ? (x, y) -> Long.compare(sizeDesc(x), sizeDesc(y))
                        : (x, y) -> Long.compare(sizeAsc(x), sizeAsc(y));
                break;
            case 3: // 修改时间
                cmp = (x, y) -> Long.compare(x.file.lastModified(), y.file.lastModified());
                if (a.sortDesc) cmp = cmp.reversed();
                break;
            case 4: // 类型
                cmp = (x, y) -> typeKey(x).compareTo(typeKey(y));
                if (a.sortDesc) cmp = cmp.reversed();
                break;
            default: // 重要度：同档聚在一起
                cmp = (x, y) -> Integer.compare(x.level, y.level);
                if (a.sortDesc) cmp = cmp.reversed();
                break;
        }
        return cmp.thenComparing((x, y) -> lower(title(x, a)).compareTo(lower(title(y, a))));
    }

    public int relDepth(FsNode n) {
        return Math.max(0, n.depth - dirRoot.depth - 1);
    }

    public void toggle(FsNode n) {
        if (!n.dir) return;
        if (!n.expanded) n.load(Atlas.get(c));
        n.expanded = !n.expanded;
        rebuild();
        if (onChanged != null) onChanged.run();
    }

    public void collapse(FsNode n) {
        if (n != null && n.dir) {
            n.expanded = false;
            rebuild();
        }
    }

    public int indexOf(FsNode n) { return visible.indexOf(n); }

    /** 从根到目标节点的链（按文件系统路径）。 */
    public List<FsNode> chainTo(File target) {
        List<FsNode> chain = new ArrayList<>();
        String rel = Atlas.rel(target.getAbsolutePath());
        if (rel.isEmpty()) return chain;
        FsNode cur = fsRoot;
        String[] parts = rel.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append('/');
            sb.append(part);
            if (cur.children == null) cur.load(Atlas.get(c));
            FsNode next = null;
            if (cur.children != null) {
                for (FsNode k : cur.children) {
                    if (k.name.equals(part)) { next = k; break; }
                }
            }
            if (next == null) return chain;
            chain.add(next);
            cur = next;
        }
        return chain;
    }

    @Override public int getCount() { return visible.size(); }

    @Override public FsNode getItem(int position) { return visible.get(position); }

    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        RowView row = (RowView) convertView;
        if (row == null) row = new RowView(c);
        FsNode n = visible.get(position);
        if (n.level < 0) n.level = Atlas.get(c).levelFor(n.file);
        row.bind(n, false, relDepth(n));
        row.setExpandListener(this::toggle);
        if (n.dir && n.size < 0 && !n.sizeQueued && Atlas.get(c).scanSizes && sizeListener != null) {
            n.sizeQueued = true;
            sizeListener.onNeedSize(n);
        }
        return row;
    }

    /** 当前目录下各项的重要度计数。 */
    public int[] levelCounts() {
        int[] counts = new int[6];
        FsNode src = dirRoot;
        if (src.children == null) src.load(Atlas.get(c));
        Atlas a = Atlas.get(c);
        if (src.children != null) for (FsNode n : src.children) {
            if (n.level < 0) n.level = a.levelFor(n.file);
            counts[n.level]++;
        }
        return counts;
    }
}
