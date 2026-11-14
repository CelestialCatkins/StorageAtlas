package com.dshgo.storageatlas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 重要度成分条：一眼看出这个目录里各等级的占比。 */
public class CompositionBar extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Palette p;
    private int[] counts = new int[6];
    private final float radius;

    public CompositionBar(Context c) {
        super(c);
        p = Palette.get(c);
        radius = Ui.dp(c, 3);
    }

    public void setCounts(int[] c) {
        counts = c;
        StringBuilder sb = new StringBuilder("重要度成分：");
        for (int i = 0; i < 6; i++) {
            if (c[i] > 0) sb.append(Palette.LEVEL_NAME[i]).append(c[i]).append("项，");
        }
        setContentDescription(sb.toString());
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        int total = 0;
        for (int v : counts) total += v;
        float w = getWidth(), h = getHeight();
        paint.setColor(p.hair);
        canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
        if (total <= 0) return;
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float x = 0;
        float gap = Ui.dp(getContext(), 1f);
        for (int i = 0; i < 6; i++) {
            if (counts[i] <= 0) continue;
            float seg = w * counts[i] / total;
            paint.setColor(p.level[i]);
            float right = x + seg;
            if (right > w) right = w;
            canvas.drawRect(x, 0, right, h, paint);
            x = right;
            if (x >= w) break;
            canvas.drawRect(x, 0, Math.min(w, x + gap), h, paint);
            x += gap;
        }
        canvas.restore();
    }
}
