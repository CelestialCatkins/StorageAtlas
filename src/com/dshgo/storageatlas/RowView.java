package com.dshgo.storageatlas;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** 紧凑的一行：重要度圆点 + 类型图标 + 中文名 + 原名 / 体积 + 等级名。 */
public class RowView extends LinearLayout {

    private final Guide guide;
    private final View dot;
    private final Ui.GlyphView typeIcon;
    private final TextView name;
    private final TextView meta;
    private final TextView levelLabel;
    private final FrameLayout chevronBox;
    private final Ui.GlyphView chevron;
    private final Context c;
    private final Palette p;

    private FsNode node;
    private ExpandListener expandListener;

    public RowView(Context ctx) {
        super(ctx);
        c = ctx;
        p = Palette.get(ctx);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c, 54));
        setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        setBackground(ripple());

        guide = new Guide(c);
        addView(guide, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        dot = new View(c);
        LayoutParams dlp = new LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7));
        dlp.rightMargin = Ui.dp(c, 8);
        addView(dot, dlp);

        typeIcon = new Ui.GlyphView(c, "file", p.text2, 1.7f);
        LayoutParams ilp = new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
        ilp.rightMargin = Ui.dp(c, 9);
        addView(typeIcon, ilp);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        LayoutParams colp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colp.rightMargin = Ui.dp(c, 8);
        addView(col, colp);

        name = Ui.label(c, "", 14.5f, p.text, Ui.MEDIUM);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);

        meta = Ui.label(c, "", 10.5f, p.text3, Ui.SANS);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams mlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(c, 1);
        col.addView(meta, mlp);

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(VERTICAL);
        right.setGravity(Gravity.END);
        right.setMinimumWidth(Ui.dp(c, 30));
        addView(right);

        levelLabel = Ui.label(c, "", 9.5f, p.text3, Ui.MEDIUM);
        levelLabel.setGravity(Gravity.END);
        levelLabel.setSingleLine(true);
        right.addView(levelLabel);

        chevronBox = new FrameLayout(c);
        chevron = Ui.iconButton(c, "chevron", p.text3, 14, 1.9f);
        FrameLayout.LayoutParams cgp = new FrameLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14));
        cgp.gravity = Gravity.CENTER;
        chevronBox.addView(chevron, cgp);
        LayoutParams chp = new LayoutParams(Ui.dp(c, 30), Ui.dp(c, 40));
        chp.leftMargin = Ui.dp(c, 2);
        addView(chevronBox, chp);
        chevronBox.setBackground(Ui.ripple(c, Palette.alpha(p.brand, 26), 15));
        chevronBox.setOnClickListener(v -> {
            if (node != null && expandListener != null) expandListener.onExpand(node);
        });
    }

    private RippleDrawable ripple() {
        return Ui.ripple(c, Palette.alpha(p.brand, 26), 0);
    }

    public void bind(FsNode n, boolean searchMode, int depth) {
        node = n;
        int level = n.level;

        dot.setBackground(Ui.round(p.colorFor(level), 4, c));
        guide.setDepth(depth);
        ViewGroup.LayoutParams lp = guide.getLayoutParams();
        lp.width = Ui.dp(c, 6 + depth * 14);
        guide.setLayoutParams(lp);

        Atlas atlas = Atlas.get(c);
        String zh = n.display(atlas);
        boolean hasNote = zh != null && !zh.isEmpty() && !zh.equals(n.name);

        // 备注 / 原名：按设置决定谁当主标题，另一个退到副标题
        String primary = atlas.noteFirst && hasNote ? zh : n.name;
        String secondary = atlas.noteFirst ? (hasNote ? n.name : null)
                : (hasNote ? zh : null);
        name.setText(primary);

        typeIcon.setIcon(Ui.iconFor(n.name, n.dir));
        typeIcon.setColor(n.dir ? p.text2 : p.text3);

        // 副标题：原名（如果换了位置）· 格式 · 体积/项数 · 已收藏
        StringBuilder sb = new StringBuilder();
        if (secondary != null) sb.append(secondary).append(" · ");
        sb.append(n.dir ? "文件夹" : fmtLabel(n.name));
        if (n.dir) {
            if (n.size >= 0) sb.append(" · ").append(Ui.size(n.size));
            else if (n.sizeQueued) sb.append(" · 统计中");
            else sb.append(" · ").append(n.childCount()).append(" 项");
        } else {
            sb.append(" · ").append(Ui.size(n.file.length()));
        }
        if (searchMode) {
            String rel = Atlas.rel(n.path);
            int slash = rel.lastIndexOf('/');
            if (slash > 0) sb.append(" · ").append(rel.substring(0, slash));
        }
        if (atlas.isFavorite(n.file)) sb.append(" · 已收藏");
        meta.setText(sb.toString());

        levelLabel.setText(Palette.LEVEL_SHORT[level]);
        levelLabel.setTextColor(p.level[level]);

        if (n.dir) {
            chevronBox.setVisibility(VISIBLE);
            chevron.setIcon(n.expanded ? "chevronDown" : "chevron");
            chevronBox.setContentDescription((n.expanded ? "收起 " : "展开 ") + (primary));
        } else {
            chevronBox.setVisibility(INVISIBLE);
        }
        setContentDescription(primary + (secondary != null ? "，" + secondary : "") + "，" +
                sb.toString() + "，" + Palette.LEVEL_NAME[level]);
    }

    public FsNode node() { return node; }

    public interface ExpandListener { void onExpand(FsNode n); }

    public void setExpandListener(ExpandListener l) { expandListener = l; }

    public void flash(final android.os.Handler ui) {
        setBackgroundColor(Palette.alpha(p.brand, 70));
        ui.postDelayed(new Runnable() {
            @Override public void run() { setBackground(ripple()); }
        }, 720);
    }

    /** 副标题里的格式：JPG / MP4 / APK…，认不出就写"文件"。 */
    private static String fmtLabel(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "文件";
        String e = name.substring(i + 1).toUpperCase(Locale.ROOT);
        if (e.length() > 5) return "文件";
        return e;
    }

    /** 左侧层级引导线。 */
    static class Guide extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int depth;

        Guide(Context c) {
            super(c);
            paint.setStrokeWidth(c.getResources().getDisplayMetrics().density * 0.8f);
            paint.setColor(Palette.get(c).hairStrong);
        }

        void setDepth(int d) { if (depth != d) { depth = d; invalidate(); } }

        @Override protected void onDraw(Canvas canvas) {
            float step = 14 * getResources().getDisplayMetrics().density;
            float start = 6 * getResources().getDisplayMetrics().density;
            for (int i = 0; i < depth; i++) {
                float x = start + i * step;
                canvas.drawLine(x, 0, x, getHeight(), paint);
            }
        }
    }
}
