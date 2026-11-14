package com.dshgo.storageatlas;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** 全局配色与重要度语义。深色/浅色两套，跟随系统。 */
public final class Palette {

    public static final String[] LEVEL_CHAR = {"系", "珍", "项", "般", "清", "空"};
    public static final String[] LEVEL_NAME = {"系统关键", "珍贵数据", "项目源码", "一般文件", "可清理", "空 / 回收"};
    public static final String[] LEVEL_SHORT = {"系统", "珍贵", "项目", "普通", "可清", "空置"};
    public static final String[] LEVEL_DESC = {
            "系统与应用私有数据，删除可能影响手机或 App 运行",
            "照片、文档、聊天记录、密码库等难以再生的数据",
            "代码与工程文件，删了能重建，但会花时间",
            "普通下载与媒体文件，多数可以重新获取",
            "缓存、缩略图、构建中间产物，可安全清理",
            "空目录、回收站、待删文件，占用低"
    };
    public static final String[] LEVEL_CODE = {"S", "A", "B", "C", "D", "E"};

    public final boolean dark;
    public final int bg, surface, surface2, hair, hairStrong, text, text2, text3,
            brand, brandInk, danger, warn, scrim, chipBg;
    public final int[] level = new int[6];
    public final int[] levelInk = new int[6];

    private static Palette INSTANCE;

    public static Palette get(Context c) {
        if (INSTANCE == null) {
            // 当前版本固定浅色主题
            INSTANCE = new Palette(false);
        }
        return INSTANCE;
    }

    public static void reset() { INSTANCE = null; }

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF0B0F14;
            surface = 0xFF10161E;
            surface2 = 0xFF16202A;
            hair = 0xFF1D2936;
            hairStrong = 0xFF2A3948;
            text = 0xFFEAF1F8;
            text2 = 0xFF9AACC0;
            text3 = 0xFF64788C;
            brand = 0xFF57D9A3;
            brandInk = 0xFF06231A;
            danger = 0xFFFF6B6B;
            warn = 0xFFFFC15E;
            scrim = 0xCC05080C;
            chipBg = 0x14FFFFFF;
            level[0] = 0xFFFF6B6B; level[1] = 0xFFFFC15E; level[2] = 0xFFB08CFF;
            level[3] = 0xFF7FA6C9; level[4] = 0xFF57D9A3; level[5] = 0xFF6B7A8A;
        } else {
            bg = 0xFFF4F6F9;
            surface = 0xFFFFFFFF;
            surface2 = 0xFFEDF1F5;
            hair = 0xFFE1E7EE;
            hairStrong = 0xFFCBD5E1;
            text = 0xFF101820;
            text2 = 0xFF4A5C70;
            text3 = 0xFF5B6B7C;
            brand = 0xFF0F7A5C;
            brandInk = 0xFFFFFFFF;
            danger = 0xFFB3261E;
            warn = 0xFF8A5A00;
            scrim = 0x99040A10;
            chipBg = 0x0F000000;
            level[0] = 0xFFB3261E; level[1] = 0xFF8A5A00; level[2] = 0xFF5B3FB8;
            level[3] = 0xFF35566F; level[4] = 0xFF0B6B4F; level[5] = 0xFF5A6672;
        }
        for (int i = 0; i < 6; i++) {
            levelInk[i] = dark ? 0xFF081014 : 0xFFFFFFFF;
        }
    }

    public int colorFor(int lv) { return level[Math.max(0, Math.min(5, lv))]; }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
