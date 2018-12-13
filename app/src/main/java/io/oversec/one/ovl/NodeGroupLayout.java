package io.oversec.one.ovl;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsoluteLayout;

import io.oversec.one.Core;
import io.oversec.one.acs.Tree;
import io.oversec.one.crypto.CryptoHandlerFacade;

import roboguice.util.Ln;

public class NodeGroupLayout extends AbsoluteLayout implements NodeView {
    private final OverlayDecryptView mOverlayDecryptView;
    private final CryptoHandlerFacade mCryptoHandlerFacade;
    private final Core mCore;
    private Tree.TreeNode mNode;
    private boolean mUnused;
    private boolean mIsRoot;

    public NodeGroupLayout(Core core, Tree.TreeNode node, Rect parentBoundsInScreen, OverlayDecryptView overlayDecryptView, CryptoHandlerFacade cryptoHandlerFacade) {
        this(false, core, node, parentBoundsInScreen, overlayDecryptView, cryptoHandlerFacade);
    }


    public NodeGroupLayout(boolean isRoot, Core core, Tree.TreeNode node, Rect parentBoundsInScreen, OverlayDecryptView overlayDecryptView, CryptoHandlerFacade cryptoHandlerFacade) {
        super(core.getCtx());
        mIsRoot = isRoot;
        mCore = core;
        mNode = node;
        mOverlayDecryptView = overlayDecryptView;
        mCryptoHandlerFacade = cryptoHandlerFacade;

        //--------------------- update layout / dimensions

        AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams(0, 0, 0, 0);
        calcLayoutParams(params, node, parentBoundsInScreen);
        setLayoutParams(params);


        //----------------------- update contents
        // setBackgroundColor(Color.parseColor("#3300FF00"));

        int cc = node.getChildCount();
        for (int i = 0; i < cc; i++) {
            View childView = null;
            if (node.getChildAt(i).isTextNode()) {
                childView = new NodeTextView(mCore, node.getChildAt(i), node.getBoundsInScreen(), overlayDecryptView, cryptoHandlerFacade);
            } else {
                childView = new NodeGroupLayout(mCore, node.getChildAt(i), node.getBoundsInScreen(), overlayDecryptView, cryptoHandlerFacade);
            }
            addView(childView);
        }


    }

    @Override
    public void update(Tree.TreeNode node, Rect parentBoundsInScreen) {
        mUnused = false;
        mNode.recycle(false);
        mNode = node;

        //--------------------- update layout / dimensions

        ViewGroup.LayoutParams pp = getLayoutParams();

        if (pp instanceof AbsoluteLayout.LayoutParams) {
            AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) pp;
            boolean changed = calcLayoutParams(params, node, parentBoundsInScreen);
            if (changed) {
                setLayoutParams(params);
            }
        } else {
            Ln.w("Hoooh?  LayoutParams are not from AbsoluteLayout??");
//            //how can this happen?
//            boolean changed = calcLayoutParamsRoot(pp, node, parentBoundsInScreen);
//            if (changed) {
//                setLayoutParams(pp);
//            }
        }
        //--------------------- update child views

        //mark alle existing vchild views as dirty
        int viewChildChount = getChildCount();
        for (int i = 0; i < viewChildChount; i++) {
            ((NodeView) getChildAt(i)).setUnused();
        }

        int cc = node.getChildCount();
        for (int i = 0; i < cc; i++) {
            boolean foundExisting = false;
            Tree.TreeNode nodeChild = node.getChildAt(i);

            for (int k = 0; k < getChildCount(); k++) {
                NodeView viewChild = ((NodeView) getChildAt(k));

                if (viewChild != null && viewChild.getNodeKey() == nodeChild.getKey() && viewChild.matchesNodeType(nodeChild)) {
                    viewChild.update(nodeChild, node.getBoundsInScreen());
                    foundExisting = true;
                    break;
                }
            }

            if (!foundExisting) {

                View childView = null;
                if (nodeChild.isTextNode()) {
                    childView = new NodeTextView(mCore, nodeChild, node.getBoundsInScreen(), mOverlayDecryptView, mCryptoHandlerFacade);
                } else {
                    childView = new NodeGroupLayout(mCore, nodeChild, node.getBoundsInScreen(), mOverlayDecryptView, mCryptoHandlerFacade);
                }
                addView(childView);
            }
        }

        //remove all dirty views
        for (int i = viewChildChount - 1; i >= 0; i--) {
            NodeView child = ((NodeView) getChildAt(i));
            if (child.isUnused()) {
                child.recycle();
                removeViewAt(i);
            }
        }
    }

    private boolean calcLayoutParams(AbsoluteLayout.LayoutParams params, Tree.TreeNode node, Rect parentBoundsInScreen) {
        Rect boundsInScreen = node.getBoundsInScreen();
        Rect boundsInParent = new Rect(boundsInScreen); //TODO pool
        if (!mIsRoot) {
            boundsInParent.offset(-parentBoundsInScreen.left, -parentBoundsInScreen.top); //calculate the real bounds in parent.
        }


        boolean changed = false;

        if (params.x != boundsInParent.left || params.y != boundsInParent.top || params.width != boundsInParent.width() || params.height != boundsInParent.height()) {
            params.x = boundsInParent.left;
            params.y = boundsInParent.top;
            params.width = boundsInParent.width();
            params.height = boundsInParent.height();
            changed = true;
        }

        return changed;


    }

//    private boolean calcLayoutParamsRoot(ViewGroup.LayoutParams params, Tree.TreeNode node, Rect parentBoundsInScreen) {
//
//
//        Rect boundsInScreen = node.getBoundsInScreen();
//        Rect boundsInParent = new Rect(boundsInScreen); //TODO pool
//        boundsInParent.offset(-parentBoundsInScreen.left, -parentBoundsInScreen.top); //calculate the real bounds in parent.
//
//
//
//        boolean changed = false;
//
//        if (params.width != boundsInParent.width() || params.height != boundsInParent.height()) {
//
//            params.width = boundsInParent.width();
//            params.height = boundsInParent.height();
//            changed = true;
//        }
//
//        return changed;
//
//
//    }

    @Override
    public boolean matchesNodeType(Tree.TreeNode node) {
        return !node.isTextNode();
    }

    @Override
    public Tree.TreeNode getNode() {
        return mNode;
    }


    @Override
    public void setUnused() {
        mUnused = true;
    }

    @Override
    public boolean isUnused() {
        return mUnused;
    }

    @Override
    public void recycle() {
        mNode.recycle(false);
        for (int k = 0; k < getChildCount(); k++) {
            NodeView child = ((NodeView) getChildAt(k));
            child.recycle();
        }
    }

    @Override
    public int getNodeKey() {
        return mNode.getKey();
    }


    public void makeSpaceAbove(int px) {
        AbsoluteLayout.LayoutParams lp = (LayoutParams) getLayoutParams();
        lp.y = lp.y - px;
        if (lp.y < 0) {
            ViewParent vp = getParent();
            if (vp != null && vp instanceof NodeGroupLayout) {
                NodeGroupLayout parent = (NodeGroupLayout) vp;
                parent.makeSpaceAbove(-lp.y);
                lp.y = 0;
            }
        }
        lp.height = lp.height + px;
        setLayoutParams(lp);
    }
}
