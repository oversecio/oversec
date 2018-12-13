package io.oversec.one.ovl;

import android.graphics.Rect;
import android.support.v4.content.ContextCompat;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.BaseDecryptResult;

import io.oversec.one.db.Db;

public class OverlayButtonDecryptView extends AbstractOverlayButtonInputView {

    public OverlayButtonDecryptView(Core core, String packageName) {
        super(core, packageName);

        int maxDim = Math.max(mDisplayWidth, mDisplayHeight);

        mDeltaX = mCore.getDb().getButtonDecryptDeltaX(mPackageName, 999/*WH_PX + WH_PX / 2 - WH_SMALL_PX / 2*/);
        mDeltaY = mCore.getDb().getButtonDecryptDeltaY(mPackageName, -WH_PX - WH_SMALL_PX / 2 - ENCRYPT_DECRYPT_PADDING_PX);
        mImeFullscreenX = mCore.getDb().getButtonDecryptImeFullscreenX(mPackageName, maxDim - WH_PX + WH_PX / 2 - WH_SMALL_PX / 2);
        mImeFullscreenY = mCore.getDb().getButtonDecryptImeFullscreenY(mPackageName, WH_PX - WH_SMALL_PX - ENCRYPT_DECRYPT_PADDING_PX);

        mANCHORH = mCore.getDb().getButtonDecryptAnchorH(mPackageName, ANCHORH.RIGHT);
        mANCHORV = mCore.getDb().getButtonDecryptAnchorV(mPackageName, ANCHORV.BOTTOM);
    }

    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonDecryptDeltaX(mPackageName, mDeltaX);
        mCore.getDb().setButtonDecryptDeltaY(mPackageName, mDeltaY);
        mCore.getDb().setButtonDecryptImeFullscreenX(mPackageName, mImeFullscreenX);
        mCore.getDb().setButtonDecryptImeFullscreenY(mPackageName, mImeFullscreenY);
        mCore.getDb().setButtonDecryptAnchorH(mPackageName, mANCHORH);
        mCore.getDb().setButtonDecryptAnchorV(mPackageName, mANCHORV);

    }

    protected boolean isSmall() {
        return true;
    }

    @Override
    void onSingleTap() {
        mCore.onButtonDecryptSingleTap();
    }

    @Override
    void onLongTap() {
        mCore.onButtonDecryptLongTap();
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes) {
        Rect aFocusedNodeBounds = focusedView == null ? null : focusedView.getNodeBoundsInScreen();

        boolean hideSelf = false;


        if (aFocusedNodeBounds == null || aFocusedNodeBounds.width() <= 0 || aFocusedNodeBounds.height() <= 0) {
            hideSelf = true;
        } else {


            super.onScrapeComplete(focusedView, hasVisibleNodes);
            if (focusedView != null && focusedView.getTreeNode() != null &&
                    "android.view.View".equals(focusedView.getTreeNode().getClassName())) {
                //WebViewXyzzy this is a surrogate webview, impossible to decrypt back to plain text, we can't write the text back
                //so don't show
                hideSelf = true;
            } else {
                BaseDecryptResult tdr = focusedView == null ? null : focusedView.getTryDecryptResult();
                if (tdr != null && tdr.isOk()) {
                    hideSelf = false;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        mView.setImageResource(R.drawable.ic_lock_open_black_24dp);
                    } else {
                        mView.setImageResource(R.drawable.ic_backspace_black_24dp);
                    }
                } else if (focusedView.getOrigText() != null && focusedView.getOrigText().length() > 0) {
                    hideSelf = false;
                    mView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_backspace_black_18dp));
                } else {
                    hideSelf = true;
                    mView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_lock_black_18dp));
                }
            }
        }

        hideSelf(hideSelf);
    }

    @Override
    protected void updatePosition() {
    }
}
