package com.dshgo.storageatlas;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 一个可展开的树节点。 */
public class FsNode {
    public final File file;
    public final String name;
    public final String path;
    public final boolean dir;
    public final int depth;

    public List<FsNode> children;
    public boolean expanded;
    public boolean loading;
    public long size = -1;
    public boolean sizeQueued;
    public int level = -1;
    public String note;
    public boolean lastChild;
    /** 可视层级引导线：第 k 位为 true 表示第 k 层祖先还有后续兄弟，需画通高竖线。长度 = 该行的可视层级。 */
    public boolean[] rails;

    public FsNode(File f, int depth) {
        this.file = f;
        this.name = f.getName().isEmpty() ? Atlas.ROOT : f.getName();
        this.path = f.getAbsolutePath();
        this.dir = f.isDirectory();
        this.depth = depth;
    }

    public List<FsNode> load(Atlas atlas) {
        if (children != null) return children;
        children = new ArrayList<>();
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File k : kids) children.add(new FsNode(k, depth + 1));
            Collections.sort(children, new Comparator<FsNode>() {
                @Override public int compare(FsNode a, FsNode b) {
                    if (a.dir != b.dir) return a.dir ? -1 : 1;
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
        }
        return children;
    }

    public int childCount() {
        if (children != null) return children.size();
        File[] kids = file.listFiles();
        return kids == null ? 0 : kids.length;
    }

    public String display(Atlas atlas) {
        if (note != null) return note;
        note = atlas.noteFor(file);
        return note;
    }
}
