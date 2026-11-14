package com.dshgo.storageatlas;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** 全屏搜索：文件名与中文备注一起搜，结果可直接定位回目录树。 */
public class SearchOverlay {

    private final MainActivity a;
    private final FrameLayout parent;
    private final Atlas atlas;
    private final Palette p;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout view;
    private EditText input;
    private TextView status;
    private ListView list;
    private ResultAdapter adapter;
    private final List<FsNode> results = new ArrayList<>();
    private volatile boolean cancelled;
    private volatile int generation;
    private boolean open;
    private Runnable debounce;

    public SearchOverlay(MainActivity activity, FrameLayout parent) {
        this.a = activity;
        this.parent = parent;
        this.atlas = activity.atlas;
        this.p = Palette.get(activity);
    }

    public boolean isOpen() { return open; }

    public void open() {
        open = true;
        cancelled = false;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(p.bg);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.setPadding(0, Ui.statusBar(a), 0, 0);

        // 顶栏
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        FrameLayout back = new FrameLayout(a);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        Ui.GlyphView bi = Ui.iconButton(a, "close", p.text, 20, 2f);
        FrameLayout.LayoutParams bip = new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
        bip.gravity = Gravity.CENTER;
        back.addView(bi, bip);
        back.setContentDescription("关闭搜索");
        back.setOnClickListener(v -> close());
        top.addView(back);

        LinearLayout field = new LinearLayout(a);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setBackground(Ui.stroke(p.surface, p.hairStrong, 12, 1, a));
        field.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 6));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        flp.topMargin = Ui.dp(a, 4);
        flp.bottomMargin = Ui.dp(a, 4);
        top.addView(field, flp);
        TextView lab = Ui.label(a, "搜索名称或中文备注", 10.5f, p.text3, Ui.SANS);
        field.addView(lab);
        input = new EditText(a);
        input.setHint("例如 相机、cache、DCIM");
        input.setHintTextColor(p.text3);
        input.setTextColor(p.text);
        input.setTextSize(15f);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        input.setContentDescription("搜索名称或中文备注");
        field.addView(input);
        view.addView(top);

        View hr = Ui.hairline(a, p.hair);
        view.addView(hr);

        status = Ui.label(a, "输入关键词开始搜索，文件名和中文备注都会匹配", 12f, p.text2, Ui.SANS);
        status.setPadding(Ui.dp(a, 18), Ui.dp(a, 10), Ui.dp(a, 18), Ui.dp(a, 8));
        view.addView(status);

        list = new ListView(a);
        list.setDivider(new android.graphics.drawable.ColorDrawable(p.hair));
        list.setDividerHeight(Ui.dp(a, 0.7f));
        list.setVerticalScrollBarEnabled(false);
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent1, v, position, id) -> {
            FsNode n = results.get(position);
            close();
            a.revealFile(new File(n.path));
        });
        list.setOnItemLongClickListener((parent1, v, position, id) -> {
            a.copyPath(new File(results.get(position).path));
            return true;
        });
        view.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        parent.addView(view);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a2) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                scheduleSearch(s.toString().trim());
            }
        });
        input.requestFocus();
        ui.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private void scheduleSearch(final String q) {
        if (debounce != null) ui.removeCallbacks(debounce);
        debounce = () -> startSearch(q);
        ui.postDelayed(debounce, 260);
    }

    private void startSearch(final String q) {
        cancelled = true;
        final int myGen = ++generation;
        results.clear();
        adapter.notifyDataSetChanged();
        if (q.isEmpty()) {
            status.setText("输入关键词开始搜索，文件名和中文备注都会匹配");
            return;
        }
        status.setText("搜索中…");
        cancelled = false;
        final String needle = q.toLowerCase();
        final int[] found = new int[]{0};
        final String[] phase = new String[]{"搜索中…"};
        Thread t = new Thread(() -> {
            LinkedList<File> queue = new LinkedList<>();
            queue.add(new File(Atlas.ROOT));
            int scanned = 0;
            int scannedTotal = 0;
            while (!queue.isEmpty() && !cancelled && found[0] < 400 && scannedTotal < 80000) {
                File dir = queue.poll();
                File[] kids = dir.listFiles();
                if (kids == null) continue;
                for (File f : kids) {
                    if (cancelled) break;
                    scanned++;
                    scannedTotal++;
                    if (scannedTotal > 80000) break;
                    String nm = f.getName();
                    boolean hit = nm.toLowerCase().contains(needle);
                    if (!hit) {
                        String note = atlas.noteFor(f);
                        hit = note != null && note.toLowerCase().contains(needle);
                    }
                    if (hit && found[0] < 400) {
                        FsNode n = new FsNode(f, 0);
                        n.level = atlas.levelFor(f);
                        found[0]++;
                        final FsNode fn = n;
                        ui.post(() -> {
                            if (myGen != generation) return;
                            results.add(fn);
                            adapter.notifyDataSetChanged();
                            status.setText("找到 " + results.size() + " 项" +
                                    (results.size() >= 400 ? "（仅显示前 400 项）" : ""));
                        });
                    }
                    if (f.isDirectory() && f.canRead()) queue.add(f);
                    if (scanned % 800 == 0) {
                        final int sc = scanned;
                        ui.post(() -> {
                            if (myGen != generation) return;
                            if (results.isEmpty()) status.setText("搜索中… 已扫描 " + sc + " 个文件");
                        });
                    }
                }
            }
            ui.post(() -> {
                if (cancelled || myGen != generation) return;
                if (results.isEmpty()) status.setText("没有找到匹配项。换个关键词，或检查是否漏了字。");
                else status.setText("找到 " + results.size() + " 项 · 点条目定位，长按复制路径");
            });
        });
        t.setDaemon(true);
        t.start();
    }

    public void close() {
        cancelled = true;
        open = false;
        if (view != null) {
            parent.removeView(view);
            view = null;
        }
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(parent.getWindowToken(), 0);
    }

    private class ResultAdapter extends BaseAdapter {
        @Override public int getCount() { return results.size(); }
        @Override public Object getItem(int i) { return results.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int position, View convert, ViewGroup parentV) {
            RowView row = (RowView) convert;
            if (row == null) row = new RowView(a);
            FsNode n = results.get(position);
            row.bind(n, true, 0);
            return row;
        }
    }
}
