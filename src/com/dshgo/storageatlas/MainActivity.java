package com.dshgo.storageatlas;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TreeAdapter.SizeListener {

    Atlas atlas;
    Palette p;
    FsNode root;
    TreeAdapter adapter;

    FrameLayout rootView;
    LinearLayout content;
    LinearLayout header;
    HorizontalScrollView crumbScroll;
    LinearLayout crumbBox;
    LinearLayout chipsBox;
    TextView metaLeft, metaRight;
    static MainActivity CURRENT;

    LinearLayout compPanel;
    TextView sortLabel;
    Ui.GlyphView compChevron;
    boolean compOpen = false;
    CompositionBar compBar;
    FrameLayout listWrap;
    ListView list;
    View refreshBtn;
    boolean refreshing;
    LinearLayout emptyState;
    TextView emptyTitle, emptyDesc, emptyAction;
    TextView snack;
    Runnable snackHide;
    View permissionView;
    SearchOverlay search;

    final ExecutorService io = Executors.newFixedThreadPool(3);
    final Handler ui = new Handler(Looper.getMainLooper());
    final Set<String> sizeInFlight = new HashSet<>();

    @Override protected void onCreate(Bundle b) {
        CURRENT = this;
        super.onCreate(b);
        p = Palette.get(this);
        atlas = Atlas.get(this);
        getWindow().setStatusBarColor(p.bg);
        getWindow().setNavigationBarColor(p.bg);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(p.bg);
        content = buildContent();
        rootView.addView(content);
        setContentView(rootView);
        checkPermission();
    }

    // ---------------------------------------------------------------- 界面

    private LinearLayout buildContent() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(p.bg);
        header.setPadding(0, Ui.statusBar(this), 0, 0);

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 4), Ui.dp(this, 1), Ui.dp(this, 4), 0);
        top.addView(iconBtn("back", new Runnable() {
            @Override public void run() { navUp(); }
        }));

        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbScroll.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        crumbBox = new LinearLayout(this);
        crumbBox.setOrientation(LinearLayout.HORIZONTAL);
        crumbBox.setGravity(Gravity.CENTER_VERTICAL);
        crumbScroll.addView(crumbBox);
        top.addView(crumbScroll);

        top.addView(iconBtn("sort", new Runnable() {
            @Override public void run() { Sheets.sortSheet(MainActivity.this); }
        }));
        top.addView(iconBtn("search", new Runnable() {
            @Override public void run() { openSearch(); }
        }));
        refreshBtn = iconBtn("refresh", new Runnable() {
            @Override public void run() { refresh(); }
        });
        top.addView(refreshBtn);
        top.addView(iconBtn("more", new Runnable() {
            @Override public void run() { Sheets.menu(MainActivity.this); }
        }));
        header.addView(top);

        // 元信息
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 5));
        metaLeft = Ui.label(this, "", 11.5f, p.text2, Ui.SANS);
        metaLeft.setSingleLine(true);
        metaLeft.setEllipsize(TextUtils.TruncateAt.END);
        metaLeft.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metaRow.addView(metaLeft);
        metaRight = Ui.label(this, "", 11f, p.text3, Ui.MONO);
        metaRow.addView(metaRight);

        LinearLayout compToggle = new LinearLayout(this);
        compToggle.setOrientation(LinearLayout.HORIZONTAL);
        compToggle.setGravity(Gravity.CENTER_VERTICAL);
        compToggle.setMinimumHeight(Ui.dp(this, 44));
        compToggle.setPadding(Ui.dp(this, 10), 0, 0, 0);
        TextView compLabel = Ui.label(this, "成分", 11.5f, p.text2, Ui.MEDIUM);
        compToggle.addView(compLabel);
        compChevron = Ui.iconButton(this, "chevronDown", p.text2, 11, 1.9f);
        LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(Ui.dp(this, 13), Ui.dp(this, 13));
        cclp.leftMargin = Ui.dp(this, 3);
        compToggle.addView(compChevron, cclp);
        compToggle.setOnClickListener(v -> setCompOpen(!compOpen));
        compToggle.setContentDescription("展开或收起重要度成分与筛选");
        metaRow.addView(compToggle);
        header.addView(metaRow);

        // 成分条
        compPanel = new LinearLayout(this);
        compPanel.setOrientation(LinearLayout.VERTICAL);
        compPanel.setVisibility(View.GONE);
        LinearLayout compWrap = compPanel;
        compWrap.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 2));
        compBar = new CompositionBar(this);
        compWrap.addView(compBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));
        chipsBox = new LinearLayout(this);
        chipsBox.setOrientation(LinearLayout.HORIZONTAL);
        chipsBox.setPadding(0, Ui.dp(this, 3), 0, 0);
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipScroll.addView(chipsBox);
        compWrap.addView(chipScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(compWrap);

        header.addView(Ui.hairline(this, p.hair));
        col.addView(header);

        // 列表
        listWrap = new FrameLayout(this);
        listWrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list = new ListView(this);
        // 分隔线由 RowView 自绘：子行从内容起点开始，引导线不会被列表分隔线切断
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 72));
        list.setBackgroundColor(p.bg);
        list.setOnItemClickListener((parent, view, position, id) -> {
            FsNode n = adapter.getItem(position);
            if (n.dir) {
                navigateTo(n);
            } else {
                Sheets.detail(MainActivity.this, n);
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Sheets.detail(MainActivity.this, adapter.getItem(position));
            return true;
        });
        listWrap.addView(list);

        emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyState.setPadding(Ui.dp(this, 40), 0, Ui.dp(this, 40), Ui.dp(this, 90));
        emptyState.setVisibility(View.GONE);
        emptyState.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyState.setGravity(Gravity.CENTER);
        emptyTitle = Ui.label(this, "", 16f, p.text, Ui.MEDIUM);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyDesc = Ui.label(this, "", 12.5f, p.text2, Ui.SANS);
        emptyDesc.setGravity(Gravity.CENTER);
        emptyDesc.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etp.topMargin = Ui.dp(this, 4);
        emptyState.addView(emptyTitle);
        emptyState.addView(emptyDesc, etp);
        emptyAction = Ui.label(this, "清除筛选", 13f, p.brand, Ui.MEDIUM);
        emptyAction.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        emptyAction.setBackground(Ui.stroke(Color.TRANSPARENT, p.hairStrong, 22, 1, this));
        LinearLayout.LayoutParams eap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        eap.topMargin = Ui.dp(this, 16);
        emptyState.addView(emptyAction, eap);
        emptyAction.setVisibility(View.GONE);
        emptyAction.setOnClickListener(v -> {
            adapter.filterLevel = -1;
            adapter.rebuild();
            refreshAll();
        });
        listWrap.addView(emptyState);
        col.addView(listWrap);

        snack = Ui.label(this, "", 13f, p.bg, Ui.SANS);
        snack.setBackground(Ui.round(p.text, 10, this));
        snack.setPadding(Ui.dp(this, 16), Ui.dp(this, 11), Ui.dp(this, 16), Ui.dp(this, 11));
        FrameLayout.LayoutParams snp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        snp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        snp.bottomMargin = Ui.dp(this, 28);
        snack.setLayoutParams(snp);
        snack.setAlpha(0f);
        snack.setVisibility(View.GONE);
        rootView.addView(snack);
        return col;
    }

    private View iconBtn(String id, Runnable action) {
        FrameLayout box = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        box.setLayoutParams(lp);
        box.setBackground(Ui.ripple(this, Palette.alpha(p.brand, 30), 22));
        Ui.GlyphView icon = Ui.iconButton(this, id, p.text, 22, 2.0f);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22));
        ip.gravity = Gravity.CENTER;
        box.addView(icon, ip);
        box.setOnClickListener(v -> action.run());
        box.setContentDescription(contentDesc(id));
        return box;
    }

    private String contentDesc(String id) {
        switch (id) {
            case "back": return "返回上一级";
            case "search": return "搜索文件或文件夹";
            case "refresh": return "刷新当前目录";
            case "more": return "更多功能";
            default: return id;
        }
    }

    // ---------------------------------------------------------------- 数据

    private void loadRoot() {
        if (root != null) return;
        root = new FsNode(new File(Atlas.ROOT), 0);
        root.expanded = true;
        root.load(atlas);
        root.level = 0;
        adapter = new TreeAdapter(this, root);
        adapter.sizeListener = this;
        if (atlas.mcpEnabled && McpService.server() == null) McpService.start(this);
        adapter.onChanged = this::refreshAll;
        adapter.toggleListener = this::onTreeToggle;
        list.setAdapter(adapter);
        refreshAll();
    }

    void refreshAll() {
        if (adapter == null) return;
        updateSortLabel();
        rebuildCrumbs();
        int[] counts = adapter.levelCounts();
        compBar.setCounts(counts);
        rebuildChips(counts);

        FsNode focus = adapter.dirRoot != null ? adapter.dirRoot : root;
        int dirs = 0, files = 0;
        long bytes = 0;
        if (focus.children != null) {
            for (FsNode n : focus.children) {
                if (n.dir) dirs++; else { files++; bytes += n.file.length(); }
            }
        }
        if (adapter.filterLevel >= 0) {
            metaLeft.setText("仅「" + Palette.LEVEL_SHORT[adapter.filterLevel] + "」 · "
                    + adapter.getCount() + " 项");
        } else {
            metaLeft.setText(dirs + " 个文件夹 · " + files + " 个文件");
        }
        if (adapter.filterLevel >= 0) metaRight.setText("");
        else if (focus.size > 0) metaRight.setText("共 " + Ui.size(focus.size));
        else if (!sizeInFlight.isEmpty()) metaRight.setText("统计中…");
        else metaRight.setText("");

        if (adapter.filterLevel >= 0 && !compOpen) setCompOpen(true);
        boolean empty = adapter.getCount() == 0;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (adapter.filterLevel >= 0) {
                emptyTitle.setText("筛选后没有结果");
                emptyDesc.setText("这个目录里没有「" + Palette.LEVEL_NAME[adapter.filterLevel] + "」级别的项目。");
                emptyAction.setVisibility(View.VISIBLE);
            } else {
                emptyTitle.setText("这个文件夹是空的");
                emptyDesc.setText("没有子文件夹或文件。下拉返回上一级看看别处。");
                emptyAction.setVisibility(View.GONE);
            }
        }
    }

    /** 原地展开后：空目录给提示；父行在屏幕下半部时上移，保证子项可见。 */
    private void onTreeToggle(FsNode n, boolean expanded) {
        if (!expanded) return;
        if (n.childCount() == 0) {
            snack("这个文件夹是空的");
            return;
        }
        final int idx = adapter.indexOf(n);
        if (idx < 0) return;
        list.post(new Runnable() {
            @Override public void run() {
                int first = list.getFirstVisiblePosition();
                View v = list.getChildAt(idx - first);
                if (v != null && v.getTop() > list.getHeight() * 0.55f) {
                    list.smoothScrollToPositionFromTop(idx, Ui.dp(MainActivity.this, 8), 220);
                }
            }
        });
    }

    /** 重新读取当前目录及其已展开分支；保留展开态与滚动位置。 */
    void refresh() {
        if (adapter == null || refreshing) return;
        final FsNode focus = adapter.dirRoot;
        if (focus == null) return;
        if (!focus.file.exists()) {
            goRoot();
            snack("当前目录已不存在，已返回根目录");
            return;
        }
        refreshing = true;
        if (refreshBtn != null) refreshBtn.animate().rotationBy(360f).setDuration(420).start();
        try {
            Set<String> expanded = new HashSet<>();
            collectExpanded(focus, expanded);
            int first = list.getFirstVisiblePosition();
            View firstView = list.getChildAt(0);
            int top = firstView == null ? 0 : Math.max(0, firstView.getTop());

            sizeInFlight.clear();
            atlas.dropCache(focus.path);
            invalidateTree(focus);
            adapter.rebuild();
            for (String path : expanded) {
                FsNode n = nodeAt(path);
                if (n != null && n.dir) {
                    n.expanded = true;
                    n.load(atlas);
                }
            }
            adapter.rebuild();

            focus.size = -1;
            focus.sizeQueued = false;
            if (atlas.scanSizes && focus != root) {
                focus.sizeQueued = true;
                onNeedSize(focus);
            }
            refreshAll();
            list.setSelectionFromTop(first, top);
            snack("已刷新");
        } catch (Exception e) {
            snack("刷新失败：可能没有权限或路径已变化");
        } finally {
            refreshing = false;
        }
    }

    private void collectExpanded(FsNode n, Set<String> out) {
        if (n.children == null) return;
        for (FsNode k : n.children) {
            if (k.dir && k.expanded) out.add(k.path);
            collectExpanded(k, out);
        }
    }

    /** 清掉子树里的目录列表与备注/等级/体积缓存，让下一次重建真正读盘。 */
    private void invalidateTree(FsNode n) {
        if (n.children != null) for (FsNode k : n.children) invalidateTree(k);
        n.children = null;
        n.size = -1;
        n.sizeQueued = false;
        n.level = -1;
        n.note = null;
    }

    /** 按绝对路径在当前树里找节点，逐级 load，不依赖刷新前的旧对象。 */
    private FsNode nodeAt(String path) {
        String rel = Atlas.rel(path);
        FsNode cur = adapter.fsRoot;
        if (rel.isEmpty()) return cur;
        for (String part : rel.split("/")) {
            cur.load(atlas);
            FsNode next = null;
            if (cur.children != null) {
                for (FsNode k : cur.children) {
                    if (k.name.equals(part)) { next = k; break; }
                }
            }
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    /** AI 通过 MCP 改了备注/等级后，把界面刷一遍。 */
    void refreshFromMcp() {
        runOnUiThread(() -> {
            if (adapter == null) return;
            adapter.rebuild();
            refreshAll();
        });
    }

    void updateSortLabel() {
        if (sortLabel == null) return;
        sortLabel.setText(Atlas.SORT_NAMES[atlas.sortMode] + (atlas.sortDesc ? "↓" : "↑"));
    }

    /** 排序 / 名称显示等设置变更后重排列表。 */
    void applySettings() {
        if (adapter == null) return;
        adapter.rebuild();
        list.setSelection(0);
        refreshAll();
        updateSortLabel();
    }

    void setCompOpen(boolean open) {
        compOpen = open;
        if (compPanel != null) compPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        if (compChevron != null) compChevron.setIcon(open ? "chevronUp" : "chevronDown");
    }

    private void rebuildCrumbs() {
        crumbBox.removeAllViews();
        FsNode focus = adapter != null && adapter.dirRoot != null ? adapter.dirRoot : root;
        List<FsNode> chain = new ArrayList<>();
        if (focus != root) chain = adapter.chainTo(focus.file);
        crumbBox.addView(crumb("手机存储", focus == root, new Runnable() {
            @Override public void run() { goRoot(); }
        }));
        for (int i = 0; i < chain.size(); i++) {
            final FsNode n = chain.get(i);
            TextView sep = Ui.label(this, "›", 13f, p.text3, Ui.SANS);
            sep.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            crumbBox.addView(sep);
            crumbBox.addView(crumb(displayOf(n), i == chain.size() - 1, new Runnable() {
                @Override public void run() { navigateTo(n); }
            }));
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private TextView crumb(String text, boolean current, Runnable r) {
        TextView t = Ui.label(this, text, 13f, current ? p.text : p.text2,
                current ? Ui.MEDIUM : Ui.SANS);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(Ui.dp(this, 190));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        t.setBackground(Ui.ripple(this, Palette.alpha(p.brand, 26), 10));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void rebuildChips(int[] counts) {
        chipsBox.removeAllViews();
        int total = 0;
        for (int c : counts) total += c;
        chipsBox.addView(filterItem("全部", -1, p.text2));
        for (int i = 0; i < 6; i++) {
            chipsBox.addView(filterItem(Palette.LEVEL_SHORT[i], i, p.level[i]));
        }
    }

    private View filterItem(String label, final int lv, int color) {
        boolean on = adapter.filterLevel == lv;
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(Ui.dp(this, 38));
        item.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        item.setBackground(on ? Ui.round(Palette.alpha(color, 24), 9, this)
                : Ui.ripple(this, Palette.alpha(color, 22), 9));
        if (lv >= 0) {
            View dot = new View(this);
            dot.setBackground(Ui.round(color, 3, this));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 5), Ui.dp(this, 5));
            dlp.rightMargin = Ui.dp(this, 4);
            item.addView(dot, dlp);
        }
        TextView t = Ui.label(this, label, 11f, on ? color : p.text2, Ui.MEDIUM);
        item.addView(t);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = Ui.dp(this, 1);
        item.setLayoutParams(ilp);
        item.setContentDescription(label + (on ? "，已筛选" : ""));
        item.setOnClickListener(v -> {
            adapter.filterLevel = (adapter.filterLevel == lv) ? -1 : lv;
            adapter.rebuild();
            refreshAll();
        });
        return item;
    }

    // ---------------------------------------------------------------- 导航

    private String displayOf(FsNode n) {
        String zh = n.display(atlas);
        return (zh == null || zh.isEmpty()) ? n.name : zh;
    }

    private void goRoot() {
        collapseAll(root);
        adapter.setDirRoot(root);
        refreshAll();
        list.setSelection(0);
    }

    private void collapseAll(FsNode n) {
        if (n.children != null) for (FsNode k : n.children) {
            k.expanded = false;
            collapseAll(k);
        }
    }

    private void navigateTo(FsNode target) {
        List<FsNode> chain = adapter.chainTo(target.file);
        for (int i = 0; i + 1 < chain.size(); i++) collapseDeeper(chain.get(i), chain.get(i + 1));
        adapter.setDirRoot(target);
        refreshAll();
        list.setSelection(0);
    }

    private void collapseDeeper(FsNode n, FsNode target) {
        if (n.children == null) return;
        for (FsNode k : n.children) {
            if (k != target) { k.expanded = false; collapseAll(k); }
        }
    }

    void navUp() {
        if (adapter == null) { finish(); return; }
        FsNode cur = adapter.dirRoot;
        if (cur == null || cur == adapter.fsRoot) { finish(); return; }
        List<FsNode> chain = adapter.chainTo(cur.file);
        FsNode parent = chain.size() >= 2 ? chain.get(chain.size() - 2) : adapter.fsRoot;
        cur.expanded = false;
        adapter.setDirRoot(parent);
        refreshAll();
        int idx = adapter.indexOf(cur);
        if (idx >= 0) list.setSelection(Math.max(0, idx - 2));
    }

    void revealFile(File f) {
        if (adapter == null) return;
        List<FsNode> chain = adapter.chainTo(f);
        if (chain.isEmpty()) { snack("无法定位：" + f.getName()); return; }
        final FsNode target = chain.get(chain.size() - 1);
        FsNode parent = chain.size() >= 2 ? chain.get(chain.size() - 2) : adapter.fsRoot;
        parent.load(atlas);
        adapter.setDirRoot(parent);
        refreshAll();
        int idx = adapter.indexOf(target);
        if (idx >= 0) {
            list.setSelection(Math.max(0, idx - 3));
            ui.postDelayed(() -> {
                int first = list.getFirstVisiblePosition();
                View v = list.getChildAt(idx - first);
                if (v instanceof RowView) ((RowView) v).flash(ui);
            }, 260);
        }
    }

    void copyPath(File f) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("路径", f.getAbsolutePath()));
        snack("已复制 " + f.getAbsolutePath());
    }

    void toggleFavorite(FsNode n) {
        atlas.toggleFavorite(n.file);
        adapter.notifyDataSetChanged();
        snack(atlas.isFavorite(n.file) ? "已收藏" : "已取消收藏");
    }

    void snack(String msg) {
        snack.setText(msg);
        snack.setVisibility(View.VISIBLE);
        snack.animate().alpha(1f).translationY(0).setDuration(160).start();
        if (snackHide != null) ui.removeCallbacks(snackHide);
        snackHide = () -> snack.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> snack.setVisibility(View.GONE)).start();
        ui.postDelayed(snackHide, 2600);
    }

    void deleteNode(FsNode n) {
        final File f = n.file;
        final FsNode parent = parentOf(n);
        snack("正在删除…");
        io.execute(() -> {
            long[] stat = new long[2];
            final boolean ok = deleteRec(f, stat);
            ui.post(() -> {
                if (!ok) { snack("删除失败：可能没有权限或文件被占用"); return; }
                atlas.dropCache(f.getAbsolutePath());
                if (parent != null) {
                    parent.children = null;
                    if (parent.expanded) parent.load(atlas);
                }
                adapter.rebuild();
                refreshAll();
                snack("已删除 " + stat[1] + " 项 · " + Ui.size(stat[0]));
            });
        });
    }

    private FsNode parentOf(FsNode n) {
        List<FsNode> chain = adapter.chainTo(n.file);
        if (chain.size() >= 2) return chain.get(chain.size() - 2);
        return root;
    }

    /** 供 MCP 工具复用的递归删除。 */
    public static boolean deleteRecursive(File f) {
        return deleteRecStatic(f);
    }

    private static boolean deleteRecStatic(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecStatic(k);
        }
        return f.delete();
    }

    private boolean deleteRec(File f, long[] stat) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k, stat);
        }
        boolean ok = f.delete();
        if (ok) { stat[0] += f.isDirectory() ? 0 : f.length(); stat[1]++; }
        return ok;
    }

    // ---------------------------------------------------------------- 体积

    @Override public void onNeedSize(FsNode n) {
        if (!atlas.scanSizes) return;
        if (!sizeInFlight.add(n.path)) return;
        io.execute(() -> {
            final long s = atlas.totalSizeOf(n.file);
            ui.post(() -> {
                sizeInFlight.remove(n.path);
                n.size = s;
                n.sizeQueued = false;
                if (adapter != null) adapter.notifyDataSetChanged();
                refreshAll();
            });
        });
    }

    // ---------------------------------------------------------------- 权限

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) {
                hidePermission();
                loadRoot();
            } else showPermission();
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                loadRoot();
            } else {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
            }
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 7 && res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadRoot();
        } else if (code == 7) {
            showPermission();
        }
    }

    @Override protected void onDestroy() {
        if (CURRENT == this) CURRENT = null;
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        Palette.reset();
        p = Palette.get(this);
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            hidePermission();
            if (root == null) loadRoot();
        }
    }

    private void showPermission() {
        if (permissionView != null) return;
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(p.bg);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.dp(this, 28), Ui.statusBar(this), Ui.dp(this, 28), Ui.dp(this, 40));
        v.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(Palette.alpha(p.brand, 24), 28, this));
        Ui.GlyphView icon = Ui.iconButton(this, "folder", p.brand, 30, 2.0f);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        ip.gravity = Gravity.CENTER;
        badge.addView(icon, ip);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64));
        blp.bottomMargin = Ui.dp(this, 26);
        v.addView(badge, blp);

        TextView title = Ui.label(this, "需要「所有文件访问」权限", 22f, p.text, Ui.MEDIUM);
        v.addView(title);

        TextView body = Ui.label(this,
                "存储星图要读取完整的目录树，才能在没打开文件夹前就告诉你哪里重要、哪里能清理。\n\n"
                        + "① 点下方按钮进入系统设置\n② 找到「存储星图」并打开「允许访问所有文件」\n③ 回到这里会自动开始读取\n\n"
                        + "数据只在本机解析，不上传、不改动任何文件。",
                13.5f, p.text2, Ui.SANS);
        body.setLineSpacing(Ui.dp(this, 6), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(this, 14);
        bp.bottomMargin = Ui.dp(this, 30);
        v.addView(body, bp);

        TextView primary = Ui.label(this, "去系统设置开启", 15f, p.brandInk, Ui.MEDIUM);
        primary.setGravity(Gravity.CENTER);
        primary.setBackground(Ui.round(p.brand, 24, this));
        primary.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        primary.setContentDescription("去系统设置开启所有文件访问权限");
        primary.setOnClickListener(x -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        });
        v.addView(primary);

        TextView retry = Ui.label(this, "我已开启，重新检测", 14f, p.text2, Ui.SANS);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        retry.setOnClickListener(x -> {
            if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
                hidePermission();
                loadRoot();
            } else snack("还没有开启，请在系统设置里找到本应用");
        });
        v.addView(retry);

        permissionView = v;
        rootView.addView(v);
    }

    private void hidePermission() {
        if (permissionView != null) {
            rootView.removeView(permissionView);
            permissionView = null;
        }
    }

    // ---------------------------------------------------------------- 搜索

    void openSearch() {
        if (root == null) return;
        search = new SearchOverlay(this, rootView);
        search.open();
    }

    // ---------------------------------------------------------------- 收藏

    void openFavorite(File f) {
        revealFile(f);
    }
}
