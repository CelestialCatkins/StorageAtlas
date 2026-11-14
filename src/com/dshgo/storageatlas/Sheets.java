package com.dshgo.storageatlas;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** 各类底部面板与确认框。 */
public final class Sheets {

    // ------------------------------------------------------------ 基础容器

    static Dialog sheet(Activity a, View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.42f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        return d;
    }

    static LinearLayout sheetRoot(Activity a) {
        Palette p = Palette.get(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(topRound(p.bg, a));
        box.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 22) + Ui.navBar(a));
        View grab = new View(a);
        grab.setBackground(Ui.round(p.hairStrong, 2, a));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(a, 12);
        box.addView(grab, gp);
        return box;
    }

    static GradientDrawable topRound(int color, Activity a) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), 0, 0, 0, 0});
        return d;
    }

    static TextView title(Activity a, String s) {
        TextView t = Ui.label(a, s, 18f, Palette.get(a).text, Ui.MEDIUM);
        t.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 14));
        return t;
    }

    static TextView subtitle(Activity a, String s) {
        TextView t = Ui.label(a, s, 12.5f, Palette.get(a).text2, Ui.SANS);
        t.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        t.setLineSpacing(Ui.dp(a, 3), 1f);
        return t;
    }

    static LinearLayout actionRow(Activity a, String iconId, String label, String trailing,
                                  int color, Runnable r) {
        Palette p = Palette.get(a);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(a, 52));
        row.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 6));
        row.setContentDescription(label);
        row.setBackground(Ui.ripple(a, Palette.alpha(p.brand, 20), 0));
        Ui.GlyphView ic = Ui.iconButton(a, iconId, color, 21, 1.9f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(a, 21), Ui.dp(a, 21));
        ilp.rightMargin = Ui.dp(a, 15);
        row.addView(ic, ilp);
        TextView t = Ui.label(a, label, 14.5f, color, Ui.SANS);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        if (trailing != null) {
            TextView tr = Ui.label(a, trailing, 12f, p.text3, Ui.SANS);
            row.addView(tr);
        }
        row.setOnClickListener(v -> r.run());
        return row;
    }

    // ------------------------------------------------------------ 详情

    public static void detail(final MainActivity a, final FsNode n) {
        final Palette p = Palette.get(a);
        final Atlas atlas = a.atlas;
        LinearLayout box = sheetRoot(a);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        TextView chip = new TextView(a);
        chip.setText(Palette.LEVEL_SHORT[n.level]);
        chip.setTextSize(9.5f);
        chip.setTypeface(Ui.MEDIUM);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(p.levelInk[n.level]);
        chip.setBackground(Ui.round(p.colorFor(n.level), 5, a));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 21));
        clp.rightMargin = Ui.dp(a, 10);
        head.addView(chip, clp);
        String zh = n.display(atlas);
        TextView name = Ui.label(a, (zh == null || zh.isEmpty()) ? n.name : zh, 19f, p.text, Ui.MEDIUM);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name);
        box.addView(head);

        TextView path = Ui.label(a, n.path, 11f, p.text3, Ui.MONO);
        path.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 16));
        path.setTextIsSelectable(true);
        box.addView(path);

        // 重要度说明
        LinearLayout lv = new LinearLayout(a);
        lv.setOrientation(LinearLayout.VERTICAL);
        lv.setBackgroundColor(Palette.alpha(p.colorFor(n.level), 18));
        lv.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lvp.leftMargin = Ui.dp(a, 14);
        lvp.rightMargin = Ui.dp(a, 14);
        lvp.bottomMargin = Ui.dp(a, 14);
        TextView lvName = Ui.label(a, Palette.LEVEL_NAME[n.level], 13f, p.level[n.level], Ui.MEDIUM);
        TextView lvDesc = Ui.label(a, Palette.LEVEL_DESC[n.level], 12f, p.text2, Ui.SANS);
        lvDesc.setLineSpacing(Ui.dp(a, 2), 1f);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.topMargin = Ui.dp(a, 4);
        lv.addView(lvName);
        lv.addView(lvDesc, dp);
        box.addView(lv, lvp);

        // 元信息
        String sizeText = n.dir ? (n.size > 0 ? Ui.size(n.size) : "未统计")
                : Ui.size(n.file.length());
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new Date(n.file.lastModified()));
        String info = (n.dir ? ("子项 " + n.childCount() + " 项") : "文件") + "  ·  " + sizeText
                + "\n修改时间  " + time;
        TextView meta = Ui.label(a, info, 12f, p.text2, Ui.SANS);
        meta.setLineSpacing(Ui.dp(a, 4), 1f);
        meta.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 10));
        box.addView(meta);
        box.addView(Ui.hairline(a, p.hair));

        final Dialog d = sheet(a, box);
        box.addView(actionRow(a, "copy", "复制绝对路径", null, p.text, () -> {
            a.copyPath(n.file);
            d.dismiss();
        }));
        if (!n.dir) {
            box.addView(actionRow(a, "doc", "阅读内容",
                    "文本 / 图片", p.text, () -> {
                        d.dismiss();
                        reader(a, n);
                    }));
        }
        box.addView(actionRow(a, "edit", "编辑中文备注", atlas.customNoteFor(n.file) != null ? "已自定义" : null,
                p.text, () -> {
                    d.dismiss();
                    editNote(a, n);
                }));
        boolean fav = atlas.isFavorite(n.file);
        box.addView(actionRow(a, "star", fav ? "取消收藏" : "收藏到常用", null, p.text, () -> {
            a.toggleFavorite(n);
            d.dismiss();
        }));
        if (n.dir) {
            box.addView(actionRow(a, n.expanded ? "chevronDown" : "chevron",
                    n.expanded ? "收起这个文件夹" : "展开这个文件夹", null, p.text, () -> {
                        a.adapter.toggle(n);
                        a.refreshAll();
                        d.dismiss();
                    }));
        }
        box.addView(Ui.hairline(a, p.hair));
        box.addView(actionRow(a, "trash", "删除", "不可恢复", p.danger, () -> {
            d.dismiss();
            confirmDelete(a, n);
        }));
        d.show();
    }


    // ------------------------------------------------------------ 阅读

    private static final String[] IMG_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"};

    public static void reader(final MainActivity a, final FsNode n) {
        final Palette p = Palette.get(a);
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "阅读内容"));

        TextView path = Ui.label(a, n.path, 11f, p.text3, Ui.MONO);
        path.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 8));
        path.setTextIsSelectable(true);
        box.addView(path);

        String name = n.file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.58f);
        long size = n.file.length();
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new Date(n.file.lastModified()));

        boolean isImg = false;
        for (String e : IMG_EXT) if (e.equals(ext)) { isImg = true; break; }

        if (isImg) {
            ImageView iv = new ImageView(a);
            Bitmap bm = decode(n.file, 1600);
            if (bm != null) {
                iv.setImageBitmap(bm);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iv.setBackgroundColor(Palette.alpha(p.text3, 18));
            }
            ScrollView sv = new ScrollView(a);
            sv.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxH);
            svp.leftMargin = Ui.dp(a, 16);
            svp.rightMargin = Ui.dp(a, 16);
            box.addView(sv, svp);
        } else {
            String text = readTextPreview(n.file, 200 * 1024);
            boolean looksText = text != null && !text.isEmpty();
            TextView t;
            if (looksText) {
                t = Ui.label(a, text, 12.5f, p.text, Ui.MONO);
                t.setTextIsSelectable(true);
                t.setLineSpacing(Ui.dp(a, 2), 1f);
            } else {
                t = Ui.label(a, "这是二进制文件，读不出文本内容。\n\n"
                        + "· 格式  " + (ext.isEmpty() ? "未知" : ext.toUpperCase(java.util.Locale.ROOT)) + "\n"
                        + "· 体积  " + Ui.size(size) + "\n"
                        + "· 修改  " + time + "\n\n"
                        + "想让它有中文名，可以长按 →「编辑中文备注」，或者用「AI 助手」让 AI 读过内容后自动命名。",
                        12.5f, p.text2, Ui.SANS);
                t.setTextIsSelectable(true);
                t.setLineSpacing(Ui.dp(a, 3), 1f);
            }
            t.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
            ScrollView sv = new ScrollView(a);
            sv.addView(t, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxH);
            svp.leftMargin = Ui.dp(a, 16);
            svp.rightMargin = Ui.dp(a, 16);
            box.addView(sv, svp);
        }

        TextView meta = Ui.label(a, "格式 " + (ext.isEmpty() ? "未知" : ext.toUpperCase(java.util.Locale.ROOT))
                + "  ·  " + Ui.size(size) + "  ·  " + time, 11f, p.text3, Ui.SANS);
        meta.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 12));
        box.addView(meta);
        box.addView(Ui.hairline(a, p.hair));

        final Dialog d = sheet(a, box);
        box.addView(actionRow(a, "copy", "复制绝对路径", null, p.text, () -> {
            a.copyPath(n.file);
            d.dismiss();
        }));
        box.addView(actionRow(a, "close", "关闭", null, p.text, d::dismiss));
        d.show();
    }

    private static Bitmap decode(File f, int maxDim) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxDim || o.outHeight / (sample * 2) >= maxDim) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readTextPreview(File f, int max) {
        try {
            long len = Math.min(f.length(), max);
            if (len <= 0) return "";
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            int bad = 0;
            for (int i = 0; i < off; i++) {
                int b = buf[i] & 0xff;
                if (b == 0) return "";
                if (b < 9 || (b > 13 && b < 32)) bad++;
            }
            if (off > 0 && bad * 100 / off > 2) return "";
            String s = new String(buf, 0, off, java.nio.charset.StandardCharsets.UTF_8);
            if (f.length() > max) s += "\n\n…（只显示前 200 KB）";
            return s;
        } catch (Throwable t) {
            return "";
        }
    }


    // ------------------------------------------------------------ 改端口

    public static void editPort(final MainActivity a) {
        final Palette p = Palette.get(a);
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(p.bg, 20, a));
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 22), Ui.dp(a, 16));

        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Ui.dp(a, 320), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
        }

        card.addView(Ui.label(a, "修改服务端口", 16f, p.text, Ui.MEDIUM));
        TextView hint = Ui.label(a, "只监听 127.0.0.1（或局域网开关打开时的 0.0.0.0）。"
                + "建议 1024–65535，改完服务会自动重启。", 11.5f, p.text3, Ui.SANS);
        hint.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
        hint.setLineSpacing(Ui.dp(a, 2), 1f);
        card.addView(hint);

        final EditText input = new EditText(a);
        input.setText(a.atlas.mcpPort);
        input.setSingleLine(true);
        input.setTextSize(16f);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(p.text);
        input.setBackground(Ui.stroke(p.surface2, p.hairStrong, 10, 1, a));
        input.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        card.addView(input);

        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(a, 14);
        card.addView(btns, bp);

        TextView cancel = Ui.label(a, "取消", 14f, p.text2, Ui.MEDIUM);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.stroke(Color.TRANSPARENT, p.hairStrong, 22, 1, a));
        TextView save = Ui.label(a, "保存并重启", 14f, p.brandInk, Ui.MEDIUM);
        save.setGravity(Gravity.CENTER);
        save.setBackground(Ui.round(p.brand, 22, a));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
        lp1.rightMargin = Ui.dp(a, 10);
        btns.addView(cancel, lp1);
        btns.addView(save, new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f));
        cancel.setOnClickListener(v -> d.dismiss());
        save.setOnClickListener(v -> {
            String v2 = input.getText().toString().trim();
            int port;
            try { port = Integer.parseInt(v2); } catch (Exception e) { a.snack("端口必须是数字"); return; }
            if (port < 1024 || port > 65535) { a.snack("端口范围 1024–65535"); return; }
            a.atlas.setMcp("mcp_port", String.valueOf(port));
            d.dismiss();
            if (menuDialog != null && menuDialog.isShowing()) menuDialog.dismiss();
            McpService.stop(a);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> McpService.start(a), 350);
            a.snack("端口已改为 " + port + "，服务重启中");
            a.getWindow().getDecorView().postDelayed(() -> mcpPanel(a), 700);
        });
        d.show();
    }

    // ------------------------------------------------------------ AI（MCP）面板

    public static void mcpPanel(final MainActivity a) {
        if (menuDialog != null && menuDialog.isShowing()) menuDialog.dismiss();
        final Palette p = Palette.get(a);
        final Atlas atlas = a.atlas;
        final LinearLayout box = sheetRoot(a);
        box.addView(title(a, "AI 助手（MCP）"));

        TextView desc = subtitle(a, "让本机 AI 连接这个 App：读目录、读文件内容、批量写中文备注、改重要度。"
                + "服务只监听 127.0.0.1，离开这台手机无法访问。");
        desc.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 12));
        box.addView(desc);

        final boolean[] on = {McpService.server() != null && McpService.server().isRunning()};
        final TextView state = Ui.label(a, on[0] ? "运行中" : "已停止", 12f,
                on[0] ? p.brand : p.text3, Ui.MEDIUM);
        LinearLayout svc = new LinearLayout(a);
        svc.setOrientation(LinearLayout.HORIZONTAL);
        svc.setGravity(Gravity.CENTER_VERTICAL);
        svc.setMinimumHeight(Ui.dp(a, 52));
        svc.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        svc.setBackground(Ui.ripple(a, Palette.alpha(p.brand, 20), 0));
        Ui.GlyphView gear = Ui.iconButton(a, "gear", p.text, 21, 1.9f);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(a, 21), Ui.dp(a, 21));
        glp.rightMargin = Ui.dp(a, 15);
        svc.addView(gear, glp);
        TextView svcLabel = Ui.label(a, "AI 接口服务", 14.5f, p.text, Ui.SANS);
        svcLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        svc.addView(svcLabel);
        svc.addView(state);
        svc.setOnClickListener(v -> {
            boolean want = !(McpService.server() != null && McpService.server().isRunning());
            atlas.setMcp("mcp_enabled", want);
            if (want) McpService.start(a); else McpService.stop(a);
            on[0] = want;
            state.setText(want ? "运行中" : "已停止");
            state.setTextColor(want ? p.brand : p.text3);
            a.snack(want ? "AI 接口已开启 · 127.0.0.1:" + atlas.mcpPort : "AI 接口已关闭");
        });
        box.addView(svc);

        // 服务器地址：直接平铺显示，长按即复制（不另加复制按钮）
        LinearLayout addrCard = new LinearLayout(a);
        addrCard.setOrientation(LinearLayout.VERTICAL);
        // 按下/长按时有涟漪反馈，松手后触发复制
        addrCard.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.alpha(p.brand, 32)),
                Ui.stroke(p.surface2, p.hair, 12, 1, a), null));
        addrCard.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acp.leftMargin = Ui.dp(a, 20);
        acp.rightMargin = Ui.dp(a, 20);
        acp.bottomMargin = Ui.dp(a, 10);

        LinearLayout headRow = new LinearLayout(a);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView addrLabel = Ui.label(a, "服务器地址", 10.5f, p.text3, Ui.MEDIUM);
        addrLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headRow.addView(addrLabel);
        TextView portBtn = Ui.label(a, "端口 " + atlas.mcpPort + " ✎", 11f, p.brand, Ui.MEDIUM);
        portBtn.setPadding(Ui.dp(a, 8), Ui.dp(a, 2), 0, Ui.dp(a, 2));
        portBtn.setOnClickListener(v -> editPort(a));
        headRow.addView(portBtn);
        addrCard.addView(headRow);

        final String localUrl = "http://127.0.0.1:" + atlas.mcpPort + "/mcp";
        final TextView addrValue = Ui.label(a, localUrl, 12.5f, p.text, Ui.MONO);
        addrValue.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 2));
        addrCard.addView(addrValue);

        final String ip = McpServer.localIp();
        final String lanUrl = ip.isEmpty() ? "" : "http://" + ip + ":" + atlas.mcpPort + "/mcp";
        final String lanText = ip.isEmpty() ? "局域网 未连接 WiFi"
                : (atlas.mcpLan ? "局域网 " + lanUrl : "局域网 未开启（在下方打开）");
        final TextView lanValue = Ui.label(a, lanText, 11.5f, p.text3, Ui.MONO);
        lanValue.setPadding(0, Ui.dp(a, 2), 0, 0);
        addrCard.addView(lanValue);

        TextView addrHint = Ui.label(a, "ⓘ 长按地址或令牌即可复制", 10.5f, p.brand, Ui.MEDIUM);
        addrHint.setBackground(Ui.round(Palette.alpha(p.brand, 18), 8, a));
        addrHint.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(a, 8);
        addrCard.addView(addrHint, hlp);

        View.OnLongClickListener copyLocal = v -> {
            copyText(a, localUrl, "已复制 " + localUrl);
            return true;
        };
        addrValue.setOnLongClickListener(copyLocal);
        addrCard.setOnLongClickListener(copyLocal);
        lanValue.setOnLongClickListener(v -> {
            if (!atlas.mcpLan) { a.snack("先在下方打开「允许局域网访问」"); return true; }
            if (lanUrl.isEmpty()) { a.snack("还没连 WiFi"); return true; }
            copyText(a, lanUrl, "已复制 " + lanUrl);
            return true;
        });
        box.addView(addrCard, acp);

        // 令牌与客户端配置：直接显示，长按即可选中复制
        LinearLayout keyCard = new LinearLayout(a);
        keyCard.setOrientation(LinearLayout.VERTICAL);
        keyCard.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 8));
        TextView keyLabel = Ui.label(a, "访问令牌", 10.5f, p.text3, Ui.MEDIUM);
        keyCard.addView(keyLabel);
        TextView keyValue = Ui.label(a, atlas.mcpToken, 12.5f, p.text, Ui.MONO);
        keyValue.setOnLongClickListener(v -> {
            copyText(a, atlas.mcpToken, "已复制令牌");
            return true;
        });
        keyValue.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        keyCard.addView(keyValue);

        String cfgUrl = (atlas.mcpLan && !McpServer.localIp().isEmpty())
                ? "http://" + McpServer.localIp() + ":" + atlas.mcpPort + "/mcp" : localUrl;
        String cfg = "{\n  \"mcpServers\": {\n    \"storage-atlas\": {\n"
                + "      \"type\": \"http\",\n      \"url\": \"" + cfgUrl + "\",\n"
                + "      \"headers\": { \"Authorization\": \"Bearer " + atlas.mcpToken + "\" }\n"
                + "    }\n  }\n}";
        TextView cfgValue = Ui.label(a, cfg, 10.5f, p.text2, Ui.MONO);
        cfgValue.setOnLongClickListener(v -> {
            copyText(a, cfg, "已复制 MCP 配置");
            return true;
        });
        cfgValue.setPadding(0, Ui.dp(a, 4), 0, 0);
        LinearLayout cfgBlock = new LinearLayout(a);
        cfgBlock.setOrientation(LinearLayout.VERTICAL);
        cfgBlock.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 6));
        cfgBlock.addView(cfgValue);
        box.addView(keyCard);
        box.addView(foldSection(a, p, "MCP 客户端配置", cfgBlock, false));

        box.addView(Ui.hairline(a, p.hair));

        final boolean[] lanOn = {atlas.mcpLan};
        final TextView lanTrail = Ui.label(a, lanOn[0] ? "允许" : "禁止", 12f,
                lanOn[0] ? p.brand : p.text3, Ui.MEDIUM);
        LinearLayout lanRow = toggleRow(a, "gear", "允许局域网访问", lanTrail);
        lanRow.setOnClickListener(v -> {
            lanOn[0] = !lanOn[0];
            atlas.setMcp("mcp_lan", lanOn[0]);
            lanTrail.setText(lanOn[0] ? "允许" : "禁止");
            lanTrail.setTextColor(lanOn[0] ? p.brand : p.text3);
            // 重启服务让新绑定生效
            McpService.stop(a);
            McpService.start(a);
            a.snack(lanOn[0] ? "已允许局域网访问，重启服务生效" : "已限制为本机访问");
        });
        box.addView(lanRow);

        final boolean[] write = {atlas.mcpAllowWrite};
        final TextView writeTrail = Ui.label(a, write[0] ? "允许" : "禁止", 12f,
                write[0] ? p.brand : p.text3, Ui.MEDIUM);
        LinearLayout wr = toggleRow(a, "edit", "允许 AI 写备注 / 改重要度", writeTrail);
        wr.setOnClickListener(v -> {
            write[0] = !write[0];
            atlas.setMcp("mcp_allow_write", write[0]);
            writeTrail.setText(write[0] ? "允许" : "禁止");
            writeTrail.setTextColor(write[0] ? p.brand : p.text3);
        });
        box.addView(wr);

        final boolean[] del = {atlas.mcpAllowDelete};
        final TextView delTrail = Ui.label(a, del[0] ? "允许" : "禁止", 12f,
                del[0] ? p.danger : p.text3, Ui.MEDIUM);
        LinearLayout dr = toggleRow(a, "trash", "允许 AI 删除文件", delTrail);
        dr.setOnClickListener(v -> {
            del[0] = !del[0];
            atlas.setMcp("mcp_allow_delete", del[0]);
            delTrail.setText(del[0] ? "允许" : "禁止");
            delTrail.setTextColor(del[0] ? p.danger : p.text3);
            if (del[0]) a.snack("注意：AI 删除不会进回收站");
        });
        box.addView(dr);

        box.addView(Ui.hairline(a, p.hair));


        String[][] tools = {
                {"list_dir", "列目录"},
                {"read_file", "读文件内容"},
                {"read_files", "批量读文件"},
                {"search_files", "搜索"},
                {"set_note", "单个写备注"},
                {"set_notes", "批量写备注"},
                {"set_level", "改重要度"},
                {"set_favorite", "收藏"},
                {"create_folder", "新建文件夹"},
                {"rename_path", "重命名"},
                {"delete_path", "删除（需开启）"},
                {"storage_overview", "总览"},
                {"annotate_guide", "取名规则"},
        };
        TextView toolList = Ui.label(a, "", 11.5f, p.text2, Ui.SANS);
        StringBuilder sb = new StringBuilder();
        for (String[] t : tools) sb.append("· ").append(t[0]).append("　").append(t[1]).append("\n");
        toolList.setText(sb.toString());
        toolList.setLineSpacing(Ui.dp(a, 3), 1f);
        toolList.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 10));
        LinearLayout toolsBlock = new LinearLayout(a);
        toolsBlock.setOrientation(LinearLayout.VERTICAL);
        toolsBlock.addView(toolList);
        box.addView(foldSection(a, p, "可调用工具（" + tools.length + "）", toolsBlock, false));

        McpServer srv = McpService.server();
        if (srv != null && !srv.logLines().isEmpty()) {
            TextView logTitle = Ui.label(a, "最近调用", 12f, p.text2, Ui.MEDIUM);
            logTitle.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 4));
            box.addView(logTitle);
            StringBuilder lb = new StringBuilder();
            java.util.List<String> lines = srv.logLines();
            for (int i = 0; i < Math.min(6, lines.size()); i++) lb.append(lines.get(i)).append("\n");
            TextView logv = Ui.label(a, lb.toString(), 10.5f, p.text3, Ui.MONO);
            logv.setLineSpacing(Ui.dp(a, 2), 1f);
            logv.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 8));
            box.addView(logv);
        }

        // 内容较多：除标题外整体可滚动，避免超出屏幕
        LinearLayout inner = new LinearLayout(a);
        inner.setOrientation(LinearLayout.VERTICAL);
        while (box.getChildCount() > 2) {
            View v = box.getChildAt(2);
            box.removeViewAt(2);
            inner.addView(v);
        }
        ScrollView scroll = new ScrollView(a);
        scroll.addView(inner, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.68f);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));

        menuDialog = sheet(a, box);
        menuDialog.show();
    }

    /** 可折叠的小节：点标题展开/收起，默认收起时只占一行。 */
    private static LinearLayout foldSection(final Activity a, final Palette p,
                                            String title, View content, boolean open) {
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(Ui.dp(a, 44));
        head.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 6));
        head.setBackground(Ui.ripple(a, Palette.alpha(p.brand, 18), 0));
        TextView label = Ui.label(a, title, 12f, p.text2, Ui.MEDIUM);
        label.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(label);
        final TextView toggle = Ui.label(a, open ? "收起 ▴" : "展开 ▾",
                10.5f, p.brand, Ui.MEDIUM);
        head.addView(toggle);
        content.setVisibility(open ? View.VISIBLE : View.GONE);
        head.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "收起 ▴" : "展开 ▾");
        });
        wrap.addView(head);
        wrap.addView(content);
        return wrap;
    }

    private static LinearLayout toggleRow(Activity a, String icon, String label, TextView trailing) {
        Palette p = Palette.get(a);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(a, 52));
        row.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        row.setBackground(Ui.ripple(a, Palette.alpha(p.brand, 20), 0));
        Ui.GlyphView ic = Ui.iconButton(a, icon, p.text, 21, 1.9f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(a, 21), Ui.dp(a, 21));
        ilp.rightMargin = Ui.dp(a, 15);
        row.addView(ic, ilp);
        TextView t = Ui.label(a, label, 14.5f, p.text, Ui.SANS);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        row.addView(trailing);
        row.setContentDescription(label);
        return row;
    }

    private static void copyText(Activity a, String text, String msg) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) a.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
        }
        if (a instanceof MainActivity) ((MainActivity) a).snack(msg);
    }

    // ------------------------------------------------------------ 备注编辑

    public static void editNote(final MainActivity a, final FsNode n) {
        final Palette p = Palette.get(a);
        final Atlas atlas = a.atlas;
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "编辑中文备注"));

        TextView path = Ui.label(a, n.path, 11f, p.text3, Ui.MONO);
        path.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 12));
        box.addView(path);

        TextView lab = Ui.label(a, "备注名", 12f, p.text2, Ui.MEDIUM);
        lab.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 6));
        box.addView(lab);

        final EditText input = new EditText(a);
        input.setHint("例如：相机照片、项目源码");
        input.setText(atlas.customNoteFor(n.file) == null ? "" : atlas.customNoteFor(n.file));
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setTextColor(p.text);
        input.setHintTextColor(p.text3);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackground(Ui.stroke(p.surface2, p.hairStrong, 10, 1, a));
        input.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = Ui.dp(a, 20);
        ilp.rightMargin = Ui.dp(a, 20);
        box.addView(input, ilp);

        TextView hint = Ui.label(a, "留空保存则恢复内置名称；备注只保存在这台手机上。",
                11.5f, p.text3, Ui.SANS);
        hint.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 18));
        box.addView(hint);

        final Dialog d = sheet(a, box);
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        TextView cancel = Ui.label(a, "取消", 14f, p.text2, Ui.MEDIUM);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.stroke(Color.TRANSPARENT, p.hairStrong, 22, 1, a));
        TextView save = Ui.label(a, "保存", 14f, p.brandInk, Ui.MEDIUM);
        save.setGravity(Gravity.CENTER);
        save.setBackground(Ui.round(p.brand, 22, a));
        save.setContentDescription("保存备注");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f);
        lp1.rightMargin = Ui.dp(a, 10);
        btns.addView(cancel, lp1);
        btns.addView(save, new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f));
        box.addView(btns);
        cancel.setOnClickListener(v -> d.dismiss());
        save.setOnClickListener(v -> {
            atlas.setCustomNote(n.file, input.getText().toString());
            n.note = null;
            a.adapter.notifyDataSetChanged();
            a.refreshAll();
            a.snack("备注已保存");
            d.dismiss();
        });
        d.show();
        input.requestFocus();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    // ------------------------------------------------------------ 删除确认

    public static void confirmDelete(final MainActivity a, final FsNode n) {
        final Palette p = Palette.get(a);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(p.bg, 20, a));
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 22), Ui.dp(a, 18));

        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Ui.dp(a, 340), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
        }

        TextView t = Ui.label(a, "删除这个" + (n.dir ? "文件夹" : "文件") + "？", 17f, p.text, Ui.MEDIUM);
        card.addView(t);
        TextView sub = Ui.label(a, n.display(a.atlas), 13.5f, p.danger, Ui.MEDIUM);
        sub.setPadding(0, Ui.dp(a, 8), 0, 0);
        card.addView(sub);
        TextView path = Ui.label(a, n.path, 11f, p.text3, Ui.MONO);
        path.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 14));
        card.addView(path);
        TextView warn = Ui.label(a, n.dir
                ? "将删除该文件夹及其中全部内容。删除后不可恢复，也不会进入手机回收站。"
                : "删除后不可恢复，也不会进入手机回收站。", 12.5f, p.text2, Ui.SANS);
        warn.setLineSpacing(Ui.dp(a, 3), 1f);
        card.addView(warn);

        final CheckBox check = new CheckBox(a);
        check.setText("我已确认，这是不可恢复的删除");
        check.setTextSize(12.5f);
        check.setTextColor(p.text2);
        check.setButtonTintList(ColorStateList.valueOf(p.danger));
        check.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 6));
        card.addView(check);

        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Ui.dp(a, 10), 0, 0);
        TextView cancel = Ui.label(a, "取消", 14f, p.text2, Ui.MEDIUM);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.stroke(Color.TRANSPARENT, p.hairStrong, 22, 1, a));
        final TextView del = Ui.label(a, "删除", 14f, p.brandInk, Ui.MEDIUM);
        del.setGravity(Gravity.CENTER);
        del.setBackground(Ui.round(p.danger, 22, a));
        del.setAlpha(0.45f);
        del.setEnabled(false);
        del.setContentDescription("确认删除");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f);
        lp1.rightMargin = Ui.dp(a, 10);
        btns.addView(cancel, lp1);
        btns.addView(del, new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f));
        card.addView(btns);

        check.setOnCheckedChangeListener((b, on) -> {
            del.setEnabled(on);
            del.setAlpha(on ? 1f : 0.45f);
        });
        cancel.setOnClickListener(v -> d.dismiss());
        del.setOnClickListener(v -> {
            d.dismiss();
            a.deleteNode(n);
        });
        d.show();
    }

    // ------------------------------------------------------------ 图例

    public static void legend(final MainActivity a) {
        Palette p = Palette.get(a);
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "重要度怎么看"));
        box.addView(subtitle(a, "按对手机运行和数据的客观重要程度分六档。颜色之外还有汉字标记，色弱也能分辨。"));
        View sp = new View(a);
        box.addView(sp, new LinearLayout.LayoutParams(1, Ui.dp(a, 14)));

        int[] counts = a.adapter != null ? a.adapter.levelCounts() : new int[6];
        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(Ui.dp(a, 20), Ui.dp(a, 10), Ui.dp(a, 20), Ui.dp(a, 10));
            TextView chip = new TextView(a);
            chip.setText(Palette.LEVEL_SHORT[i]);
            chip.setTextSize(9.5f);
            chip.setTypeface(Ui.MEDIUM);
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(p.levelInk[i]);
            chip.setBackground(Ui.round(p.level[i], 5, a));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 21));
            clp.topMargin = Ui.dp(a, 3);
            clp.rightMargin = Ui.dp(a, 12);
            row.addView(chip, clp);
            LinearLayout txt = new LinearLayout(a);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout n1 = new LinearLayout(a);
            n1.setOrientation(LinearLayout.HORIZONTAL);
            n1.setGravity(Gravity.CENTER_VERTICAL);
            TextView nm = Ui.label(a, Palette.LEVEL_NAME[i], 14f, p.text, Ui.MEDIUM);
            n1.addView(nm);
            TextView cnt = Ui.label(a, "  当前目录 " + counts[i], 10.5f, p.text3, Ui.SANS);
            n1.addView(cnt);
            TextView ds = Ui.label(a, Palette.LEVEL_DESC[i], 12f, p.text2, Ui.SANS);
            ds.setLineSpacing(Ui.dp(a, 2), 1f);
            txt.addView(n1);
            txt.addView(ds);
            row.addView(txt);
            col.addView(row);
        }
        sv.addView(col);
        box.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 340)));
        Dialog d = sheet(a, box);
        d.show();
    }

    // ------------------------------------------------------------ 菜单

    public static void menu(final MainActivity a) {
        final Palette p = Palette.get(a);
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "设置与工具"));

        box.addView(actionRow(a, "info", "重要度图例", "六档说明", p.text,
                () -> {
                    legend(a);
                    dismissFrom(a);
                }));
        box.addView(actionRow(a, "gear", "AI 助手（MCP）",
                McpService.server() != null && McpService.server().isRunning()
                        ? "127.0.0.1:" + a.atlas.mcpPort : "未开启",
                p.text, () -> {
                    dismissFrom(a);
                    mcpPanel(a);
                }));
        box.addView(actionRow(a, "star", "收藏夹", String.valueOf(a.atlas.favorites().size()) + " 项", p.text,
                () -> {
                    favorites(a);
                    dismissFrom(a);
                }));
        final boolean[] scan = {a.atlas.scanSizes};
        final TextView scanTrail = Ui.label(a, scan[0] ? "已开启" : "已关闭", 12f, p.text3, Ui.SANS);
        LinearLayout scanRow = new LinearLayout(a);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setGravity(Gravity.CENTER_VERTICAL);
        scanRow.setMinimumHeight(Ui.dp(a, 52));
        scanRow.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
        Ui.GlyphView gear = Ui.iconButton(a, "gear", p.text, 21, 1.9f);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(a, 21), Ui.dp(a, 21));
        glp.rightMargin = Ui.dp(a, 15);
        scanRow.addView(gear, glp);
        TextView scanLabel = Ui.label(a, "自动统计体积", 14.5f, p.text, Ui.SANS);
        scanLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        scanRow.addView(scanLabel);
        scanRow.addView(scanTrail);
        scanRow.setContentDescription("自动统计体积，当前" + scanTrail.getText());
        scanRow.setOnClickListener(v -> {
            scan[0] = !scan[0];
            a.atlas.setScanSizes(scan[0]);
            scanTrail.setText(scan[0] ? "已开启" : "已关闭");
            a.snack(scan[0] ? "已开启体积统计" : "已关闭体积统计");
        });
        box.addView(scanRow);

        box.addView(actionRow(a, "trash", "清除自定义备注",
                a.atlas.customNoteCount() + " 条", p.text, () -> {
                    clearNotes(a);
                    dismissFrom(a);
                }));
        box.addView(Ui.hairline(a, p.hair));
        box.addView(actionRow(a, "info", "关于存储星图", "v1.2", p.text, () -> {
            about(a);
            dismissFrom(a);
        }));
        menuDialog = sheet(a, box);
        menuDialog.show();
    }

    private static Dialog menuDialog;

    // ------------------------------------------------------------ 排序与名称显示

    public static void sortSheet(final MainActivity a) {
        final Palette p = Palette.get(a);
        final Atlas atlas = a.atlas;
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "排序与名称显示"));

        for (int i = 0; i < Atlas.SORT_NAMES.length; i++) {
            final int mode = i;
            final boolean on = atlas.sortMode == i;
            box.addView(actionRow(a, on ? "check" : "", Atlas.SORT_NAMES[i], Atlas.SORT_HINTS[i],
                    on ? p.brand : p.text, () -> {
                        boolean desc = (mode == atlas.sortMode) ? atlas.sortDesc : Atlas.defaultDesc(mode);
                        atlas.setSort(mode, desc);
                        dismissFrom(a);
                        a.applySettings();
                    }));
        }

        box.addView(Ui.hairline(a, p.hair));

        box.addView(actionRow(a, atlas.sortDesc ? "chevronDown" : "chevronUp",
                "排序方向", atlas.sortDesc ? "倒序 ↓" : "正序 ↑", p.text, () -> {
                    atlas.setSort(atlas.sortMode, !atlas.sortDesc);
                    dismissFrom(a);
                    a.applySettings();
                }));

        box.addView(actionRow(a, "edit", "主标题",
                atlas.noteFirst ? "备注优先" : "原名优先", p.text, () -> {
                    atlas.setNoteFirst(!atlas.noteFirst);
                    dismissFrom(a);
                    a.applySettings();
                }));

        menuDialog = sheet(a, box);
        menuDialog.show();
    }

    private static void dismissFrom(Activity a) {
        if (menuDialog != null && menuDialog.isShowing()) menuDialog.dismiss();
        menuDialog = null;
    }

    // ------------------------------------------------------------ 收藏

    public static void favorites(final MainActivity a) {
        final Palette p = Palette.get(a);
        final List<File> favs = a.atlas.favorites();
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "收藏夹"));
        final Dialog d = sheet(a, box);
        if (favs.isEmpty()) {
            TextView empty = Ui.label(a, "还没有收藏。长按任意文件或文件夹，选「收藏到常用」。",
                    13f, p.text2, Ui.SANS);
            empty.setPadding(Ui.dp(a, 20), Ui.dp(a, 10), Ui.dp(a, 20), Ui.dp(a, 26));
            empty.setLineSpacing(Ui.dp(a, 3), 1f);
            box.addView(empty);
        } else {
            ScrollView sv = new ScrollView(a);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            for (final File f : favs) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(a, 56));
                row.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 6));
                LinearLayout col2 = new LinearLayout(a);
                col2.setOrientation(LinearLayout.VERTICAL);
                col2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                String zh = a.atlas.noteFor(f);
                TextView nm = Ui.label(a, zh == null ? f.getName() : zh, 14f, p.text, Ui.MEDIUM);
                TextView pth = Ui.label(a, Atlas.rel(f.getAbsolutePath()), 10.5f, p.text3, Ui.MONO);
                col2.addView(nm);
                col2.addView(pth);
                row.addView(col2);
                row.setContentDescription("打开收藏 " + (zh == null ? f.getName() : zh));
                row.setOnClickListener(v -> {
                    d.dismiss();
                    a.openFavorite(f);
                });
                TextView rm = Ui.label(a, "移除", 12f, p.text3, Ui.SANS);
                rm.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10));
                rm.setContentDescription("移除收藏 " + f.getName());
                rm.setOnClickListener(v -> {
                    a.atlas.toggleFavorite(f);
                    a.snack("已移出收藏");
                    d.dismiss();
                });
                row.addView(rm);
                col.addView(row);
                col.addView(Ui.hairline(a, p.hair));
            }
            sv.addView(col);
            box.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 320)));
        }
        d.show();
    }

    // ------------------------------------------------------------ 关于

    public static void about(final MainActivity a) {
        Palette p = Palette.get(a);
        LinearLayout box = sheetRoot(a);
        box.addView(title(a, "关于存储星图"));
        TextView body = Ui.label(a,
                "把手机存储画成一张可以读懂的星图：每个文件夹按重要度着色，中文备注优先显示，"
                        + "长条目告诉你它是什么、能不能删。\n\n"
                        + "· 内置 " + a.atlas.builtinCount() + " 条中文名称\n"
                        + "· 自定义备注 " + a.atlas.customNoteCount() + " 条\n"
                        + "· 所有数据只在本机读取，不上传、不修改文件\n"
                        + "· 删除操作需要勾选确认，且不可恢复\n"
                        + "· 1.1 新增：AI 助手（MCP）与文件阅读\n"
                        + "· 1.2 新增：目录翻页、批量读文件、未备注统计、批量重要度\n\n"
                        + "版本 1.2 · 数据只留在这台手机",
                12.5f, p.text2, Ui.SANS);
        body.setLineSpacing(Ui.dp(a, 4), 1f);
        body.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 16));
        box.addView(body);
        sheet(a, box).show();
    }

    // ------------------------------------------------------------ 清除备注

    public static void clearNotes(final MainActivity a) {
        Palette p = Palette.get(a);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(p.bg, 20, a));
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 22), Ui.dp(a, 18));
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Ui.dp(a, 330), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
        }
        card.addView(Ui.label(a, "清除全部自定义备注？", 16f, p.text, Ui.MEDIUM));
        TextView sub = Ui.label(a, "共 " + a.atlas.customNoteCount() + " 条。清除后这些条目会恢复显示内置中文名。",
                12.5f, p.text2, Ui.SANS);
        sub.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 18));
        sub.setLineSpacing(Ui.dp(a, 3), 1f);
        card.addView(sub);
        LinearLayout btns = new LinearLayout(a);
        TextView cancel = Ui.label(a, "取消", 14f, p.text2, Ui.MEDIUM);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.stroke(Color.TRANSPARENT, p.hairStrong, 22, 1, a));
        TextView ok = Ui.label(a, "清除", 14f, p.brandInk, Ui.MEDIUM);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.round(p.danger, 22, a));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f);
        lp1.rightMargin = Ui.dp(a, 10);
        btns.addView(cancel, lp1);
        btns.addView(ok, new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f));
        card.addView(btns);
        cancel.setOnClickListener(v -> d.dismiss());
        ok.setOnClickListener(v -> {
            a.atlas.clearCustomNotes();
            if (a.adapter != null) a.adapter.notifyDataSetChanged();
            a.refreshAll();
            a.snack("已清除自定义备注");
            d.dismiss();
        });
        d.show();
    }
}