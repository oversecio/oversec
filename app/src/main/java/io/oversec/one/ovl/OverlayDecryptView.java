package io.oversec.one.ovl;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import io.oversec.one.Core;
import io.oversec.one.common.MainPreferences;
import io.oversec.one.R;
import io.oversec.one.acs.DisplayNodeVisitor;
import io.oversec.one.acs.Tree;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;

public class OverlayDecryptView extends OverlayView implements
        IDecryptOverlayLayoutParamsChangedListener {

    public static final int TEXT_SIZE_BASE = 6;
    private final int mDrawablePadding;

    private int mTextColor;
    private int mBgColor;
    private float mTextSize;
    private float mCornerRadius;
    private int mPaddingTop;
    private int mPaddingLeft;

    private int mRightDrawableWidth;
    private boolean mShowStatusIcon;
    private NodeGroupLayout mRootNodeView;
    private CryptoHandlerFacade mCryptoHandlerFacade;

    public OverlayDecryptView(Core core, String packageName) {

        super(core, packageName);

        mCryptoHandlerFacade = core.getEncryptionHandler();

        //mCore.getDb().addDecryptOverlayLayoutParamsChangedListener(this); NOT, is called through Overlays and thus guaranteed to happen on UI thread

        Resources r = getResources();
        mDrawablePadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, r.getDisplayMetrics());

        refreshDesignParams();
    }

    @Override
    protected WindowManager.LayoutParams createLayoutParams(Context ctx) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.packageName = ctx.getPackageName();
        layoutParams.alpha = 1;

        layoutParams.type = getOverlayType();
        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (!MainPreferences.INSTANCE.isAllowScreenshots(getContext())) {
            layoutParams.flags = layoutParams.flags | WindowManager.LayoutParams.FLAG_SECURE;
        }


        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.format = PixelFormat.TRANSLUCENT;

        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        return layoutParams;
    }


    @Override
    public boolean requestSendAccessibilityEvent(View view,
                                                 AccessibilityEvent event) {
        // Never send accessibility events.
        return false;
    }

    public static int getUserInteractionRequiredText(boolean loong) {
        return loong ? R.string.decrypt_error_user_interaction_required__long : R.string.decrypt_error_user_interaction_required;
    }

    public void onOneNodeDecrypted(NodeTextView view, BaseDecryptResult tdr, boolean uirq, String origText) {
        if (uirq && origText != null) {
            mCore.addUserInteractionRequired_UI(origText, true);
        }
    }

    public NodeTextView refresh(Tree.TreeNode displayRoot) {
        if (isHidden()) {
            removeAllViews();
            if (mRootNodeView != null) {
                removeView(mRootNodeView);
                mRootNodeView.recycle();
                mRootNodeView = null;
                return null;
            }
            return null;
        }

        if (mRootNodeView == null) {

            if (displayRoot != null) {
                mRootNodeView = new NodeGroupLayout(true, mCore, displayRoot, displayRoot.getBoundsInScreen(), this, mCryptoHandlerFacade);
                addView(mRootNodeView);
            }
        } else {
            if (displayRoot != null) {
                mRootNodeView.update(displayRoot, displayRoot.getBoundsInScreen());
            } else {
                removeAllViews();
                removeView(mRootNodeView);
                mRootNodeView.recycle();
                mRootNodeView = null;
            }
        }

        return findFocusedView(this);
    }

    private void applyDesign(ViewGroup g) {
        int cc = g.getChildCount();
        for (int i = 0; i < cc; i++) {
            View v = g.getChildAt(i);
            if (v instanceof ViewGroup) {
                applyDesign((ViewGroup) v);
            } else if (v instanceof NodeTextView) {
                applyDesign((NodeTextView) v);
            }
        }
    }

    public void applyDesign(NodeTextView v) {

        v.setBackgroundColorChecked(mBgColor);
        v.setDefaultBackgroundColor(mBgColor);

        v.setCornerRadiusChecked(mCornerRadius);

        v.setTextSizeChecked(mTextSize);

        v.setTextColorChecked(mTextColor);
        v.setDefaultTextColor(mTextColor);

        v.setPaddingTopChecked(mPaddingTop);
        v.setPaddingLeftChecked(mPaddingLeft);

        v.setRightDrawableWidthChecked(mRightDrawableWidth);


        v.setCompoundDrawablePaddingChecked(mDrawablePadding);

        v.setShowStatusIconChecked(mShowStatusIcon);
    }

    public void onOrientationChanged(int orientation) {
    }

    public void destroy() {

        if (mRootNodeView != null) {
            removeView(mRootNodeView);
            mRootNodeView.recycle();
        }
    }

    @Override
    public void onDecryptOverlayLayoutParamsChanged(String packagename) {
        refreshDesignParams();
        applyDesign(this);

    }


    private void refreshDesignParams() {
        mShowStatusIcon = mCore.getDb().isShowStatusIcon(mPackageName);
        mRightDrawableWidth = mShowStatusIcon ? mCore.dipToPixels(24) : 0;
        mTextColor = mCore.getDb().getDecryptOverlayTextColor(mPackageName);
        mBgColor = mCore.getDb().getDecryptOverlayBgColor(mPackageName);
        mTextSize = TEXT_SIZE_BASE + mCore.getDb().getDecryptOverlayTextSize(mPackageName);
        mCornerRadius = mCore.dipToPixels(mCore.getDb().getDecryptOverlayCornerRadius(mPackageName));
        mPaddingTop = mCore.dipToPixels(mCore.getDb().getDecryptOverlayPaddingTop(mPackageName));
        mPaddingLeft = mCore.dipToPixels(mCore.getDb().getDecryptOverlayPaddingLeft(mPackageName));

    }

    public void visitNodeViews(DisplayNodeVisitor visitor) {
        visitNodeViews(this, visitor);
    }

    private void visitNodeViews(ViewGroup g, DisplayNodeVisitor visitor) {

        int cc = g.getChildCount();
        for (int i = 0; i < cc; i++) {
            View v = g.getChildAt(i);
            if (v instanceof ViewGroup) {
                visitNodeViews((ViewGroup) v, visitor);
            } else if (v instanceof NodeTextView) {

                visitor.visit((NodeTextView) v);
            }
        }
    }

    private NodeTextView findFocusedView(ViewGroup g) {

        int cc = g.getChildCount();
        for (int i = 0; i < cc; i++) {
            View v = g.getChildAt(i);
            if (v instanceof ViewGroup) {
                NodeTextView vv = findFocusedView((ViewGroup) v);
                if (vv != null) {
                    return vv;
                }
            } else if (v instanceof NodeTextView) {
                NodeTextView vv = (NodeTextView) v;
                if (vv.isFocusedNode()) {
                    return vv;
                }
            }

        }
        return null;
    }


    private boolean mHasVisibleEncryptedNodes;

    public boolean hasVisibleEncryptedNodes(final boolean excludeFocusedEditableEditText) {
        mHasVisibleEncryptedNodes = false;
        visitNodeViews(new DisplayNodeVisitor() {
            @Override
            public void visit(NodeTextView ntv) {
                if (ntv.getNodeBoundsInScreen().height() >= 0) {
                    if (excludeFocusedEditableEditText) {
                        mHasVisibleEncryptedNodes = mHasVisibleEncryptedNodes ||
                                ((!(ntv.isEditableEditText() && ntv.isFocused())
                                        && CryptoHandlerFacade.Companion.isEncoded(mCore.getCtx(), ntv.getOrigText()))
                                );
                    } else {
                        mHasVisibleEncryptedNodes = mHasVisibleEncryptedNodes || CryptoHandlerFacade.Companion.isEncoded(mCore.getCtx(), ntv.getOrigText());
                    }
                }
            }
        });
        return mHasVisibleEncryptedNodes;
    }

}
