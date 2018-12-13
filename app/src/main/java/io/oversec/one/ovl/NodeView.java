package io.oversec.one.ovl;

import android.graphics.Rect;

import io.oversec.one.acs.Tree;

public interface NodeView {
    void setUnused();

    boolean isUnused();

    void recycle();

    int getNodeKey();

    void update(Tree.TreeNode node, Rect parentBoundsInScreen);

    boolean matchesNodeType(Tree.TreeNode node);

    Tree.TreeNode getNode();


}
