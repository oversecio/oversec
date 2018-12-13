package io.oversec.one.ovl;

import android.graphics.Rect;
import android.view.WindowManager;

import io.oversec.one.Core;
import roboguice.util.Ln;

public abstract class AbstractOverlayButtonInputView extends AbstractOverlayButtonView {

    public enum ANCHORH {
        LEFT, RIGHT
    }

    public enum ANCHORV {
        TOP, BOTTOM
    }

    protected ANCHORH mANCHORH = ANCHORH.RIGHT;
    protected ANCHORV mANCHORV = ANCHORV.BOTTOM;
    protected int mDeltaX, mDeltaY;
    protected int mImeFullscreenX, mImeFullscreenY;

    private Rect mFocusedNodeBounds;

    public AbstractOverlayButtonInputView(Core core, String packageName) {
        super(core, packageName);
    }


    @Override
    protected void storeTransientPosition(int x, int y) {
        if (mFocusedNodeBounds == null) return;
        if (isImeFullScreen()) {
            mImeFullscreenX = mLayoutParams.x;
            mImeFullscreenY = mLayoutParams.y;
        } else {
            if (mANCHORH == ANCHORH.LEFT) {
                mDeltaX = mFocusedNodeBounds.left - mLayoutParams.x;
            } else if (mANCHORH == ANCHORH.RIGHT) {
                mDeltaX = mLayoutParams.x - mFocusedNodeBounds.right;
            }
            if (mANCHORV == ANCHORV.TOP) {
                mDeltaY = mFocusedNodeBounds.top - mLayoutParams.y;
            } else if (mANCHORH == ANCHORH.RIGHT) {
                mDeltaY = mLayoutParams.y - mFocusedNodeBounds.bottom;
            }
        }
    }

    int maxDev = WH_PX * 2;

    protected void validatePosition(WindowManager.LayoutParams layoutParams) {
        if (isImeFullScreen()) {
            return;
        }

        if (mFocusedNodeBounds != null) {
            int deltaX = 0;
            int deltaY = layoutParams.y - mFocusedNodeBounds.bottom;

            if (mANCHORH == ANCHORH.LEFT) {
                deltaX = mFocusedNodeBounds.left - layoutParams.x;
                if (deltaX < -mFocusedNodeBounds.width() / 2) {
                    mANCHORH = ANCHORH.RIGHT;
                }

            } else if (mANCHORH == ANCHORH.RIGHT) {
                deltaX = layoutParams.x - mFocusedNodeBounds.right;
                if (deltaX < -mFocusedNodeBounds.width() / 2) {
                    mANCHORH = ANCHORH.LEFT;
                }
            }

            if (deltaX > maxDev) {
                if (mANCHORH == ANCHORH.RIGHT) {
                    layoutParams.x = mFocusedNodeBounds.right + maxDev;
                } else if (mANCHORH == ANCHORH.LEFT) {
                    layoutParams.x = mFocusedNodeBounds.left - maxDev;
                }
            }


            if (mANCHORV == ANCHORV.TOP) {
                deltaY = mFocusedNodeBounds.top - layoutParams.y;
                if (deltaY < -mFocusedNodeBounds.height() / 2) {
                    mANCHORV = ANCHORV.BOTTOM;
                }

            } else if (mANCHORV == ANCHORV.BOTTOM) {
                deltaY = layoutParams.y - mFocusedNodeBounds.bottom;
                if (deltaY < -mFocusedNodeBounds.height() / 2) {
                    mANCHORV = ANCHORV.TOP;
                }
            }

            if (deltaY > maxDev) {
                if (mANCHORV == ANCHORV.BOTTOM) {
                    layoutParams.y = mFocusedNodeBounds.bottom + maxDev;
                } else if (mANCHORV == ANCHORV.TOP) {
                    layoutParams.y = mFocusedNodeBounds.top - maxDev;
                }
            }


        }
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes) {
        if (focusedView == null) {
            mFocusedNodeBounds = null;
        } else {
            mFocusedNodeBounds = focusedView.getNodeBoundsInScreen();


            int anchorX = mFocusedNodeBounds.right;
            int x = anchorX + mDeltaX;
            if (mANCHORH == ANCHORH.LEFT) {
                anchorX = mFocusedNodeBounds.left;
                x = anchorX - mDeltaX;
            }

            int anchorY = mFocusedNodeBounds.bottom;
            int y = anchorY + mDeltaY;
            if (mANCHORV == ANCHORV.TOP) {
                anchorY = mFocusedNodeBounds.top;
                y = anchorY - mDeltaY;
            }


            if (isImeFullScreen()) {
                setPosition(mImeFullscreenX, mImeFullscreenY);
            } else {
                setPosition(x, y);
            }


        }
    }


    @Override
    protected void updatePosition() {
    }
}
