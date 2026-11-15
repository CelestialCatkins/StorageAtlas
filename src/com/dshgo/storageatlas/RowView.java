package com.dshgo.storageatlas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** 紧凑的一行：类型图标 + 重要度角标 + 中文名 + 原名 / 体积 + 等级名；展开时画层级引导线。 */
public class RowView extends LinearLayout {

    /** 前 3 层 20dp、4–6 层 12dp、再深 10dp；最多画 12 层，超出的用三点 + 层数提示兜底。 */
    private static final int MAX_RAIL_DEPTH = 12;
    private static final float STEP_COMFORT = 20f;
    private static final float STEP_MID = 12f;
    private static final float STEP_TIGHT = 10f;
    private static final float MAX_INDENT_DP = 156f;

    private final Guide guide;
    private final View badge;
    private final Ui.GlyphView typeIcon;
    private final TextView name;
    private final TextView meta;
    private final TextView levelLabel;
    private final FrameLayout chevronBox;
    private final Ui.GlyphView chevron;
    private final Context c;
    private final Palette p;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private FsNode node;
    private ExpandListener expandListener;
    private int depth;
    private int plotDepth;
    private float guideWidthDp;
    private boolean treeRow;
    private int fillColor = Color.TRANSPARENT;

    /** 第 level 层新增的缩进。层号固定，父子行才能对齐。 */
    static float stepDp(int level) {
        if (level < 3) return STEP_COMFORT;
        if (level < 6) return STEP_MID;
        return STEP_TIGHT;
    }

    /** depth 层内容相对列表左边的总缩进。 */
    static float indentDp(int depth) {
        float sum = 0f;
        int n = Math.min(depth, MAX_RAIL_DEPTH);
        for (int k = 0; k < n; k++) sum += stepDp(k);
        return Math.min(MAX_INDENT_DP, sum);
    }

    /** 第 level 层竖线的 x 坐标（该层缩进的中点）。 */
    static float railDp(int level) {
        return indentDp(level) + stepDp(level) / 2f;
    }

    public RowView(Context ctx) {
        super(ctx);
        c = ctx;
        p = Palette.get(ctx);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c, 54));
        setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        setWillNotDraw(false);
        dividerPaint.setColor(p.hair);
        dividerPaint.setStrokeWidth(Ui.dp(c, 0.6f));

        guide = new Guide(c);
        guide.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        guide.setOnClickListener(v -> {
            if (node != null && node.dir && expandListener != null) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                expandListener.onExpand(node);
            }
        });
        addView(guide, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        // 图标 + 右上角重要度角标（替代原先独立占位的圆点）
        FrameLayout iconBox = new FrameLayout(c);
        LayoutParams ibp = new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
        ibp.rightMargin = Ui.dp(c, 9);
        addView(iconBox, ibp);
        typeIcon = new Ui.GlyphView(c, "file", p.text2, 1.7f);
        iconBox.addView(typeIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        badge = new View(c);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7));
        blp.gravity = Gravity.TOP | Gravity.END;
        iconBox.addView(badge, blp);
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

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

        // 右侧横排：弱化的等级标签 + 箭头，逐行对齐
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        addView(right, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        levelLabel = Ui.label(c, "", 9f, p.text3, Ui.SANS);
        levelLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        levelLabel.setSingleLine(true);
        levelLabel.setMinWidth(Ui.dp(c, 22));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.rightMargin = Ui.dp(c, 4);
        right.addView(levelLabel, llp);

        chevronBox = new FrameLayout(c);
        chevron = Ui.iconButton(c, "chevron", p.text3, 14, 1.9f);
        FrameLayout.LayoutParams cgp = new FrameLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14));
        cgp.gravity = Gravity.CENTER;
        chevronBox.addView(chevron, cgp);
        right.addView(chevronBox, new LayoutParams(Ui.dp(c, 36), ViewGroup.LayoutParams.MATCH_PARENT));
        chevronBox.setBackground(Ui.ripple(c, Palette.alpha(p.brand, 26), 14));
        chevronBox.setOnClickListener(v -> {
            if (node == null) return;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (expandListener != null) expandListener.onExpand(node);
        });
    }

    public void bind(FsNode n, boolean searchMode, int depth) {
        bind(n, searchMode, depth, false);
    }

    public void bind(FsNode n, boolean searchMode, int depth, boolean treeRow) {
        node = n;
        this.depth = Math.max(0, depth);
        this.plotDepth = Math.min(this.depth, MAX_RAIL_DEPTH);
        this.guideWidthDp = indentDp(this.plotDepth);
        this.treeRow = treeRow;

        // 只高亮顶层根和真正分叉的展开行；单链中间层保持白底，避免整列绿墙
        boolean highlightParent = n.dir && n.expanded
                && (this.depth == 0 || (n.children != null && n.children.size() > 1));
        int base = this.depth > 0 ? p.surface : p.bg;
        fillColor = highlightParent ? Ui.mix(base, p.brand, 0.10f)
                : (this.depth > 0 ? p.surface : Color.TRANSPARENT);
        applyBackground();

        int level = n.level;
        int ring = fillColor == Color.TRANSPARENT ? p.bg : fillColor;
        badge.setBackground(Ui.stroke(p.colorFor(level), ring, 3.5f, 1f, c));

        boolean overflow = this.depth > MAX_RAIL_DEPTH;
        guide.setTree(plotDepth, n.rails, n.lastChild, overflow);
        ViewGroup.LayoutParams lp = guide.getLayoutParams();
        lp.width = Ui.dp(c, guideWidthDp);
        guide.setLayoutParams(lp);
        boolean guideClickable = n.dir && this.depth > 0;
        guide.setClickable(guideClickable);
        guide.setFocusable(guideClickable);
        guide.setBackground(guideClickable ? Ui.ripple(c, Palette.alpha(p.brand, 18), 0) : null);

        Atlas atlas = Atlas.get(c);
        String zh = n.display(atlas);
        boolean hasNote = zh != null && !zh.isEmpty() && !zh.equals(n.name);

        // 备注 / 原名：按设置决定谁当主标题，另一个退到副标题
        String primary = atlas.noteFirst && hasNote ? zh : n.name;
        String secondary = atlas.noteFirst ? (hasNote ? n.name : null)
                : (hasNote ? zh : null);
        name.setText(primary);

        // 父级/顶层主文本、中粗；子项次级文本、常规字重、字号小半号
        boolean parentRow = this.depth == 0 || (n.dir && n.expanded);
        name.setTextSize(parentRow ? 14.5f : 14.0f);
        name.setTypeface(parentRow ? Ui.MEDIUM : Ui.SANS);
        name.setTextColor(parentRow ? p.text : p.text2);

        typeIcon.setIcon(Ui.iconFor(n.name, n.dir));
        typeIcon.setColor(parentRow ? p.text2 : p.text3);

        // 副标题：原名（如果换了位置）· 格式 · 体积/项数 · 已收藏
        StringBuilder sb = new StringBuilder();
        if (this.depth > MAX_RAIL_DEPTH) sb.append("第 ").append(this.depth + 1).append(" 层 · ");
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
        levelLabel.setTextColor(Palette.alpha(p.level[level], 200));

        if (n.dir) {
            chevronBox.setVisibility(VISIBLE);
            chevron.setIcon(n.expanded ? "chevronDown" : "chevron");
            chevron.setColor(n.expanded ? p.brand : p.text3);
            chevronBox.setContentDescription((n.expanded ? "收起 " : "展开 ") + primary);
        } else {
            chevronBox.setVisibility(INVISIBLE);
        }

        StringBuilder desc = new StringBuilder(primary);
        if (secondary != null) desc.append("，").append(secondary);
        desc.append("，").append(sb);
        if (this.depth > 0) desc.append("，第").append(this.depth + 1).append("层");
        if (n.dir) desc.append(n.expanded ? "，已展开" : "，已收起");
        setContentDescription(desc.toString());
    }

    private void applyBackground() {
        setBackground(Ui.rippleOn(c, Palette.alpha(p.brand, 26), fillColor, 0));
    }

    public FsNode node() { return node; }

    public interface ExpandListener { void onExpand(FsNode n); }

    public void setExpandListener(ExpandListener l) { expandListener = l; }

    public void flash(final android.os.Handler ui) {
        setBackgroundColor(Palette.alpha(p.brand, 70));
        ui.postDelayed(new Runnable() {
            @Override public void run() { applyBackground(); }
        }, 720);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!treeRow) return;
        float h = getHeight();
        float guideWidth = Ui.dp(c, guideWidthDp);
        float start = depth > 0 ? Ui.dp(c, 6) + guideWidth : 0;
        float y = h - Ui.dp(c, 0.3f);
        canvas.drawLine(start, y, getWidth(), y, dividerPaint);
    }

    /** 副标题里的格式：JPG / MP4 / APK…，认不出就写"文件"。 */
    private static String fmtLabel(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "文件";
        String e = name.substring(i + 1).toUpperCase(Locale.ROOT);
        if (e.length() > 5) return "文件";
        return e;
    }

    /**
     * 左侧层级引导线：竖线用极淡虚线，横肘/L 折角用细实线。
     * 第 0..d-2 层仅在祖先还有后续兄弟时画通高线；第 d-1 层画到行中线，
     * 本行不是最后一个兄弟时继续到底，并在中线处画横肘接到内容。
     * 超过 12 层时不画假的祖先线，只画末端 L + 三个点表示更上层被压缩。
     */
    static class Guide extends View {
        private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint elbowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean[] rails;
        private int depth;
        private boolean lastChild;
        private boolean overflow;

        Guide(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            Palette p = Palette.get(c);
            int color = Ui.mix(p.hairStrong, p.text3, 0.16f);
            railPaint.setColor(color);
            railPaint.setStrokeWidth(d);
            railPaint.setStrokeCap(Paint.Cap.ROUND);
            railPaint.setPathEffect(new DashPathEffect(new float[]{4 * d, 3 * d}, 0));
            elbowPaint.setColor(color);
            elbowPaint.setStrokeWidth(d);
            elbowPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setTree(int depth, boolean[] rails, boolean lastChild, boolean overflow) {
            this.depth = depth;
            this.rails = rails;
            this.lastChild = lastChild;
            this.overflow = overflow;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float mid = getHeight() / 2f;
            float w = getWidth();
            if (overflow) {
                // 缩进已封顶：只画当前行的 L，左侧三点表示更上层被压缩
                float x = w - 6 * d;
                canvas.drawLine(x, 0, x, mid, railPaint);
                if (!lastChild) canvas.drawLine(x, mid, x, getHeight(), railPaint);
                canvas.drawLine(x, mid - 3 * d, x, mid, elbowPaint);
                canvas.drawLine(x, mid, w, mid, elbowPaint);
                float dotX = 4 * d;
                canvas.drawCircle(dotX, mid - 4 * d, 0.9f * d, elbowPaint);
                canvas.drawCircle(dotX, mid, 0.9f * d, elbowPaint);
                canvas.drawCircle(dotX, mid + 4 * d, 0.9f * d, elbowPaint);
                return;
            }
            for (int k = 0; k < depth; k++) {
                float x = railDp(k) * d;
                if (k < depth - 1) {
                    if (rails != null && k < rails.length && rails[k]) {
                        canvas.drawLine(x, 0, x, getHeight(), railPaint);
                    }
                } else {
                    canvas.drawLine(x, 0, x, mid, railPaint);
                    if (!lastChild) canvas.drawLine(x, mid, x, getHeight(), railPaint);
                    // 折角处补一小段实线，避免虚线在转角断开
                    canvas.drawLine(x, mid - 3 * d, x, mid, elbowPaint);
                    canvas.drawLine(x, mid, w, mid, elbowPaint);
                }
            }
        }
    }
}
