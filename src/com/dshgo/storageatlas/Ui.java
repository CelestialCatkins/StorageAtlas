package com.dshgo.storageatlas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DecimalFormat;

/** 尺寸、字体、形状与图标绘制的公共工具。 */
public final class Ui {

    public static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    public static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static TextView label(Context c, String text, float sizeSp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** 有边界的圆形水波：从触点扩散，裁剪在圆角矩形里，不会溢出成一个大圆。 */
    public static android.graphics.drawable.RippleDrawable ripple(Context c, int color, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(c, radiusDp));
        android.graphics.drawable.RippleDrawable rd =
                new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(color), null, mask);
        return rd;
    }

    public static GradientDrawable round(int fill, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, float radiusDp, float wDp, Context c) {
        GradientDrawable d = round(fill, radiusDp, c);
        d.setStroke(dp(c, wDp), strokeColor);
        return d;
    }

    public static View hairline(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 0.7f)));
        return v;
    }

    public static View vspace(Context c, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    public static View hspace(Context c, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dp), 1));
        return v;
    }

    public static String size(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        DecimalFormat f = new DecimalFormat(v >= 100 ? "0" : (v >= 10 ? "0.0" : "0.00"));
        return f.format(v) + " " + u[i];
    }

    public static int navBar(Context c) {
        int id = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    public static int statusBar(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    /** 24x24 视口的线性图标，统一 1.9 描边。 */
    public static void glyph(Canvas c, String id, int color, float sizePx, float strokeDp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeDp * 24f / Math.max(1f, sizePx));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        float s = sizePx / 24f;
        c.save();
        c.scale(s, s);
        Path path = new Path();
        switch (id) {
            case "back":
                path.moveTo(19, 12); path.lineTo(5.5f, 12);
                path.moveTo(11, 5.6f); path.lineTo(4.6f, 12); path.lineTo(11, 18.4f);
                c.drawPath(path, p);
                break;
            case "search":
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(10.8f, 10.8f, 5.2f, p);
                path.moveTo(15, 15); path.lineTo(20, 20);
                c.drawPath(path, p);
                break;
            case "more":
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 5.5f, 1.7f, p);
                c.drawCircle(12, 12, 1.7f, p);
                c.drawCircle(12, 18.5f, 1.7f, p);
                break;
            case "close":
                path.moveTo(6, 6); path.lineTo(18, 18);
                path.moveTo(18, 6); path.lineTo(6, 18);
                c.drawPath(path, p);
                break;
            case "chevron":
                path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19);
                c.drawPath(path, p);
                break;
            case "sort":
                // 三条递减的横线 + 向下箭头，表示"排序"
                path.moveTo(4, 7); path.lineTo(18, 7);
                path.moveTo(4, 12); path.lineTo(13, 12);
                path.moveTo(4, 17); path.lineTo(8, 17);
                c.drawPath(path, p);
                path.reset();
                p.setStyle(Paint.Style.FILL);
                path.moveTo(17, 11); path.lineTo(21, 11); path.lineTo(19, 15); path.close();
                c.drawPath(path, p);
                break;
            case "chevronDown":
                path.moveTo(5, 9); path.lineTo(12, 16); path.lineTo(19, 9);
                c.drawPath(path, p);
                break;
            case "chevronUp":
                path.moveTo(5, 15); path.lineTo(12, 8); path.lineTo(19, 15);
                c.drawPath(path, p);
                break;
            case "star":
                path = star(12, 12.5f, 8.4f, 3.6f);
                c.drawPath(path, p);
                break;
            case "starFilled":
                p.setStyle(Paint.Style.FILL);
                c.drawPath(star(12, 12.5f, 8.4f, 3.6f), p);
                break;
            case "copy": {
                RectF r1 = new RectF(9, 9, 20, 20);
                c.drawRoundRect(r1, 2.4f, 2.4f, p);
                path.moveTo(15, 9); path.lineTo(15, 6); path.lineTo(6, 6); path.lineTo(6, 15);
                path.lineTo(9, 15);
                c.drawPath(path, p);
                break;
            }
            case "edit":
                path.moveTo(5, 19); path.lineTo(6.2f, 14.6f); path.lineTo(16.2f, 4.6f);
                path.lineTo(19.4f, 7.8f); path.lineTo(9.4f, 17.8f); path.close();
                c.drawPath(path, p);
                path.moveTo(14.2f, 6.6f); path.lineTo(17.4f, 9.8f);
                c.drawPath(path, p);
                break;
            case "trash":
                path.moveTo(4.5f, 7.5f); path.lineTo(19.5f, 7.5f);
                path.moveTo(9, 7.5f); path.lineTo(9, 4.5f); path.lineTo(15, 4.5f); path.lineTo(15, 7.5f);
                path.moveTo(6.5f, 7.5f); path.lineTo(7.5f, 20); path.lineTo(16.5f, 20);
                path.lineTo(17.5f, 7.5f);
                c.drawPath(path, p);
                break;
            case "gear":
                c.drawCircle(12, 12, 3.1f, p);
                c.drawCircle(12, 12, 7.4f, p);
                break;
            case "info":
                c.drawCircle(12, 12, 8.6f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 7.6f, 1.15f, p);
                p.setStyle(Paint.Style.STROKE);
                path.moveTo(12, 11); path.lineTo(12, 16.8f);
                c.drawPath(path, p);
                break;
            case "folder":
                path.moveTo(3.2f, 7.9f);
                path.cubicTo(3.2f, 6.8f, 4.1f, 5.9f, 5.2f, 5.9f);
                path.lineTo(9.4f, 5.9f);
                path.lineTo(11.4f, 8.0f);
                path.lineTo(18.8f, 8.0f);
                path.cubicTo(19.9f, 8.0f, 20.8f, 8.9f, 20.8f, 10.0f);
                path.lineTo(20.8f, 17.6f);
                path.cubicTo(20.8f, 18.7f, 19.9f, 19.6f, 18.8f, 19.6f);
                path.lineTo(5.2f, 19.6f);
                path.cubicTo(4.1f, 19.6f, 3.2f, 18.7f, 3.2f, 17.6f);
                path.close();
                c.drawPath(path, p);
                break;
            case "image": {
                RectF fr = new RectF(3.6f, 5.2f, 20.4f, 18.8f);
                c.drawRoundRect(fr, 2.2f, 2.2f, p);
                c.drawCircle(8.6f, 9.6f, 1.5f, p);
                path.moveTo(4.4f, 17.6f); path.lineTo(9.6f, 11.6f);
                path.lineTo(13.2f, 15.6f); path.lineTo(16.2f, 12.6f); path.lineTo(20.2f, 17.2f);
                c.drawPath(path, p);
                break;
            }
            case "video": {
                RectF fr = new RectF(3.6f, 5.4f, 20.4f, 18.6f);
                c.drawRoundRect(fr, 2.2f, 2.2f, p);
                path.moveTo(10.4f, 9.2f); path.lineTo(15.4f, 12f); path.lineTo(10.4f, 14.8f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case "audio":
                path.moveTo(9.2f, 18f); path.lineTo(9.2f, 7.6f); path.lineTo(17.6f, 5.6f);
                path.lineTo(17.6f, 15.8f);
                c.drawPath(path, p);
                c.drawCircle(7.0f, 18.0f, 2.2f, p);
                c.drawCircle(15.4f, 15.8f, 2.2f, p);
                break;
            case "apk":
                path.moveTo(12f, 3.4f); path.lineTo(20.2f, 8.1f); path.lineTo(20.2f, 15.9f);
                path.lineTo(12f, 20.6f); path.lineTo(3.8f, 15.9f); path.lineTo(3.8f, 8.1f);
                path.close();
                path.moveTo(3.8f, 8.1f); path.lineTo(12f, 12.8f); path.lineTo(20.2f, 8.1f);
                path.moveTo(12f, 12.8f); path.lineTo(12f, 20.6f);
                c.drawPath(path, p);
                break;
            case "archive": {
                RectF fr = new RectF(4.0f, 4.0f, 20.0f, 20.0f);
                c.drawRoundRect(fr, 2.0f, 2.0f, p);
                path.moveTo(4.4f, 8.8f); path.lineTo(19.6f, 8.8f);
                c.drawPath(path, p);
                path.moveTo(12f, 4.4f); path.lineTo(12f, 8.8f);
                c.drawPath(path, p);
                path.moveTo(10.4f, 12.0f); path.lineTo(13.6f, 12.0f);
                path.moveTo(10.4f, 15.0f); path.lineTo(13.6f, 15.0f);
                c.drawPath(path, p);
                break;
            }
            case "code":
                path.moveTo(9.0f, 7.2f); path.lineTo(4.4f, 12f); path.lineTo(9.0f, 16.8f);
                path.moveTo(15.0f, 7.2f); path.lineTo(19.6f, 12f); path.lineTo(15.0f, 16.8f);
                path.moveTo(13.4f, 5.4f); path.lineTo(10.6f, 18.6f);
                c.drawPath(path, p);
                break;
            case "sheet": {
                RectF fr = new RectF(4.4f, 4.2f, 19.6f, 19.8f);
                c.drawRoundRect(fr, 1.8f, 1.8f, p);
                path.moveTo(4.8f, 9.2f); path.lineTo(19.2f, 9.2f);
                path.moveTo(4.8f, 14.2f); path.lineTo(19.2f, 14.2f);
                path.moveTo(9.4f, 4.6f); path.lineTo(9.4f, 19.4f);
                path.moveTo(14.6f, 4.6f); path.lineTo(14.6f, 19.4f);
                c.drawPath(path, p);
                break;
            }
            case "slides": {
                RectF fr = new RectF(4.0f, 5.2f, 20.0f, 16.8f);
                c.drawRoundRect(fr, 1.8f, 1.8f, p);
                path.moveTo(10.6f, 8.8f); path.lineTo(15.6f, 11f); path.lineTo(10.6f, 13.2f);
                path.close();
                c.drawPath(path, p);
                path.moveTo(12f, 16.8f); path.lineTo(12f, 20f);
                path.moveTo(8.4f, 20f); path.lineTo(15.6f, 20f);
                c.drawPath(path, p);
                break;
            }
            case "db":
                path.moveTo(6.4f, 6.4f);
                path.cubicTo(6.4f, 5.0f, 9.0f, 3.8f, 12f, 3.8f);
                path.cubicTo(15.0f, 3.8f, 17.6f, 5.0f, 17.6f, 6.4f);
                path.cubicTo(17.6f, 7.8f, 15.0f, 9.0f, 12f, 9.0f);
                path.cubicTo(9.0f, 9.0f, 6.4f, 7.8f, 6.4f, 6.4f);
                c.drawPath(path, p);
                path.moveTo(6.4f, 6.4f); path.lineTo(6.4f, 17.6f);
                path.cubicTo(6.4f, 19.0f, 9.0f, 20.2f, 12f, 20.2f);
                path.cubicTo(15.0f, 20.2f, 17.6f, 19.0f, 17.6f, 17.6f);
                path.lineTo(17.6f, 6.4f);
                c.drawPath(path, p);
                path.moveTo(6.4f, 12.0f);
                path.cubicTo(6.4f, 13.4f, 9.0f, 14.6f, 12f, 14.6f);
                path.cubicTo(15.0f, 14.6f, 17.6f, 13.4f, 17.6f, 12.0f);
                c.drawPath(path, p);
                break;
            case "font":
                path.moveTo(6.0f, 19.0f); path.lineTo(12f, 5.0f); path.lineTo(18.0f, 19.0f);
                path.moveTo(8.4f, 14.2f); path.lineTo(15.6f, 14.2f);
                c.drawPath(path, p);
                break;
            case "config":
                path.moveTo(4.0f, 7.0f); path.lineTo(20.0f, 7.0f);
                path.moveTo(4.0f, 12.0f); path.lineTo(20.0f, 12.0f);
                path.moveTo(4.0f, 17.0f); path.lineTo(20.0f, 17.0f);
                c.drawPath(path, p);
                c.drawCircle(9.0f, 7.0f, 1.9f, p);
                c.drawCircle(15.0f, 12.0f, 1.9f, p);
                c.drawCircle(7.5f, 17.0f, 1.9f, p);
                break;
            case "doc": {
                path.moveTo(6.0f, 3.6f); path.lineTo(13.8f, 3.6f); path.lineTo(18.0f, 7.8f);
                path.lineTo(18.0f, 20.4f); path.lineTo(6.0f, 20.4f); path.close();
                path.moveTo(13.8f, 3.6f); path.lineTo(13.8f, 7.8f); path.lineTo(18.0f, 7.8f);
                c.drawPath(path, p);
                break;
            }
            case "note":
                path.moveTo(6.0f, 3.6f); path.lineTo(13.8f, 3.6f); path.lineTo(18.0f, 7.8f);
                path.lineTo(18.0f, 20.4f); path.lineTo(6.0f, 20.4f); path.close();
                path.moveTo(13.8f, 3.6f); path.lineTo(13.8f, 7.8f); path.lineTo(18.0f, 7.8f);
                path.moveTo(8.6f, 12.0f); path.lineTo(15.4f, 12.0f);
                path.moveTo(8.6f, 15.4f); path.lineTo(15.4f, 15.4f);
                path.moveTo(8.6f, 18.8f); path.lineTo(13.0f, 18.8f);
                c.drawPath(path, p);
                break;
            case "file":
                path.moveTo(6, 3.5f); path.lineTo(14, 3.5f); path.lineTo(18, 7.5f);
                path.lineTo(18, 20.5f); path.lineTo(6, 20.5f); path.close();
                path.moveTo(14, 3.5f); path.lineTo(14, 7.5f); path.lineTo(18, 7.5f);
                c.drawPath(path, p);
                break;
            case "check":
                path.moveTo(5, 12.5f); path.lineTo(10, 17.5f); path.lineTo(19.5f, 7);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    public static String iconFor(String name, boolean dir) {
        if (dir) return "folder";
        String n = name.toLowerCase();
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1);
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp":
            case "heic": case "heif": case "avif": case "svg": case "ico":
                return "image";
            case "mp4": case "mkv": case "mov": case "avi": case "webm": case "3gp":
            case "m4v": case "flv": case "wmv":
                return "video";
            case "mp3": case "flac": case "wav": case "m4a": case "aac": case "ogg":
            case "opus": case "amr": case "wma": case "mid": case "lrc":
                return "audio";
            case "apk": case "apks": case "xapk": case "apkm": case "aab":
                return "apk";
            case "zip": case "rar": case "7z": case "tar": case "gz": case "xz":
            case "bz2": case "tgz": case "jar":
                return "archive";
            case "java": case "kt": case "kts": case "js": case "ts": case "json":
            case "html": case "htm": case "css": case "xml": case "py": case "sh":
            case "c": case "cpp": case "h": case "go": case "rs": case "php":
            case "yml": case "yaml": case "gradle": case "vue": case "dart":
                return "code";
            case "xls": case "xlsx": case "csv": case "ods":
                return "sheet";
            case "ppt": case "pptx": case "odp":
                return "slides";
            case "db": case "sqlite": case "sqlite3": case "mdb": case "realm":
                return "db";
            case "ttf": case "otf": case "woff": case "woff2":
                return "font";
            case "ini": case "conf": case "cfg": case "properties": case "toml":
                return "config";
            case "txt": case "md": case "log": case "rtf":
                return "note";
            case "doc": case "docx": case "pdf": case "odt": case "epub":
                return "doc";
            default:
                return "doc";
        }
    }

    private static Path star(float cx, float cy, float rOuter, float rInner) {
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? rOuter : rInner;
            float x = (float) (cx + r * Math.cos(a));
            float y = (float) (cy + r * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    public static class GlyphView extends View {
        private String id;
        private int color;
        private float stroke;

        public GlyphView(Context c, String id, int color, float strokeDp) {
            super(c);
            this.id = id;
            this.color = color;
            this.stroke = strokeDp;
        }

        public void setIcon(String id) { this.id = id; invalidate(); }

        public void setColor(int color) { this.color = color; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            glyph(canvas, id, color, s, stroke * getResources().getDisplayMetrics().density);
        }
    }

    public static GlyphView iconButton(Context c, String id, int color, int sizeDp, float strokeDp) {
        GlyphView v = new GlyphView(c, id, color, strokeDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    public static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
