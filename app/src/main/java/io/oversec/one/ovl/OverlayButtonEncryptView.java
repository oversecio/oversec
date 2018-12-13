package io.oversec.one.ovl;

import android.graphics.Rect;
import android.support.v4.content.ContextCompat;
import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.db.Db;

public class OverlayButtonEncryptView extends AbstractOverlayButtonInputView {

    private long mNextEncryptionWouldUsePreviousEncryptParamsTimestamp;

    public OverlayButtonEncryptView(Core core, String packageName) {
        super(core, packageName);

        int maxDim = Math.max(mDisplayWidth, mDisplayHeight);

        mDeltaX = mCore.getDb().getButtonEncryptDeltaX(mPackageName, core.getCtx().getResources().getInteger(R.integer.default_button_encrypt_delta_x)/*WH_PX*/);
        mDeltaY = mCore.getDb().getButtonEncryptDeltaY(mPackageName, core.getCtx().getResources().getInteger(R.integer.default_button_encrypt_delta_y) /*-WH_PX*/);
        mImeFullscreenX = mCore.getDb().getButtonEncryptImeFullscreenX(mPackageName, maxDim - WH_PX);
        mImeFullscreenY = mCore.getDb().getButtonEncryptImeFullscreenY(mPackageName, WH_PX);

        mANCHORH = mCore.getDb().getButtonEncryptAnchorH(mPackageName, ANCHORH.values()[core.getCtx().getResources().getInteger(R.integer.default_button_encrypt_anchor_h)]);
        mANCHORV = mCore.getDb().getButtonEncryptAnchorV(mPackageName, ANCHORV.values()[core.getCtx().getResources().getInteger(R.integer.default_button_encrypt_anchor_v)]);

        mView.setImageResource(R.drawable.ic_lock_black_24dp);
    }

    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonEncryptDeltaX(mPackageName, mDeltaX);
        mCore.getDb().setButtonEncryptDeltaY(mPackageName, mDeltaY);
        mCore.getDb().setButtonEncryptImeFullscreenX(mPackageName, mImeFullscreenX);
        mCore.getDb().setButtonEncryptImeFullscreenY(mPackageName, mImeFullscreenY);
        mCore.getDb().setButtonEncryptAnchorH(mPackageName, mANCHORH);
        mCore.getDb().setButtonEncryptAnchorV(mPackageName, mANCHORV);
    }

    @Override
    void onSingleTap() {
        GotItPreferences.Companion.getPreferences(mCore.getCtx()).setTooltipConfirmed(getContext().getString(R.string.tooltipid_buttonencrypt_initial));
        mCore.onButtonEncryptSingleTap();
    }

    @Override
    void onLongTap() {
        GotItPreferences.Companion.getPreferences(mCore.getCtx()).setTooltipConfirmed(getContext().getString(R.string.tooltipid_buttonencrypt_initial));
        mCore.onButtonEncryptLongTap();
    }

    @Override
    void onDoubleTap() {
        GotItPreferences.Companion.getPreferences(mCore.getCtx()).setTooltipConfirmed(getContext().getString(R.string.tooltipid_buttonencrypt_initial));
        mCore.onButtonEncryptDoubleTap();
    }

    @Override
    public void onScrapeComplete(final NodeTextView focusedView, final boolean hasVisibleNodes) {
        super.onScrapeComplete(focusedView, hasVisibleNodes);

        boolean hideSelf = false;
        Rect aFocusedNodeBounds = focusedView == null ? null : focusedView.getNodeBoundsInScreen();

        if (aFocusedNodeBounds == null || aFocusedNodeBounds.width() <= 0 || aFocusedNodeBounds.height() <= 0) {
            hideSelf = true;
        } else {


            BaseDecryptResult tdr = focusedView == null ? null : focusedView.getTryDecryptResult();
            if (tdr != null && tdr.isOk()) {
                hideSelf = true;
                //mView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_lock_open_black_24dp));
            } else if (focusedView.isDecrypting()) {

                return;
            } else {
                hideSelf = false;
                mView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_lock_black_24dp));
            }
        }

        hideSelf(hideSelf);

        if (hideSelf) {
        } else {
            long delta = 0;
            if (mNextEncryptionWouldUsePreviousEncryptParamsTimestamp == 0 && nextEncryptionWouldUsePreviousEncryptParams()) {
                mNextEncryptionWouldUsePreviousEncryptParamsTimestamp = System.currentTimeMillis();
            } else {
                delta = System.currentTimeMillis() - mNextEncryptionWouldUsePreviousEncryptParamsTimestamp;
            }


            String t = focusedView.getOrigText();
            if (t != null && t.length() > 0
                    && !CryptoHandlerFacade.Companion.isEncoded(getContext(), t)
                    && nextEncryptionWouldUsePreviousEncryptParams()
                    && delta > 5000
                    ) {
                mCore.showTooltip_UI(this, getResources().getString(R.string.toast_longtap_encryptionbutton), getContext().getString(R.string.tooltipid_edittext_encryptionparamsremembered), Help.ANCHOR.button_encrypt_encryptionparamsremembered, false);

            } else if (t == null || t.length() == 0 || !CryptoHandlerFacade.Companion.isEncoded(getContext(), t)
                    ) {
                mCore.showTooltip_UI(this, mCore.getCtx().getString(R.string.tooltip_buttonencrypt_initial), getContext().getString(R.string.tooltipid_buttonencrypt_initial), Help.ANCHOR.button_encrypt_initial, false);

            }
        }
    }

    private boolean nextEncryptionWouldUsePreviousEncryptParams() {
        return mCore.isNextEncryptionWouldUsePreviousEncryptParams(mPackageName);
    }

    @Override
    protected void updatePosition() {

    }

    public View getButtonView() {
        return mView;
    }
}
