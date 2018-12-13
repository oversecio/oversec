package io.oversec.one.ovl;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import io.oversec.one.Core;
import io.oversec.one.common.MainPreferences;
import io.oversec.one.R;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.encoding.pad.AbstractPadder;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;


public class OverlayEditTextBorderView extends OverlayView implements
        IDecryptOverlayLayoutParamsChangedListener {

    private static final int STROKE_DP = 3;
    private static final int EXTRA_WIDTH_DP = 3;
    private static final int EXTRA_HEIGHT_DP = 0;

    private final View mBorderView;
    private final int mStrokePx;
    private final GradientDrawable mShape;
    private final int mExtraWidthPx, mExtraHeightPx;
    private final Paint mPaint;

    private Rect mFocusedNodeBounds;
    private int mColor;
    private boolean mInsufficientPaddingShownInThisSession;
    private boolean mCorruptedEncodingShownInThisSession;

    public OverlayEditTextBorderView(Core core, String packageName) {
        super(core, packageName);
        mStrokePx = core.dipToPixels(STROKE_DP);
        mExtraWidthPx = core.dipToPixels(EXTRA_WIDTH_DP);
        mExtraHeightPx = core.dipToPixels(EXTRA_HEIGHT_DP);

        mBorderView = new View(getContext());
        addView(mBorderView);

        setVisibility(View.GONE);

        mShape = new GradientDrawable();
        mColor = mCore.getDb().getDecryptOverlayBgColor(mPackageName);
        refreshDesignParams();

        mBorderView.setBackground(mShape);

        setChildVisibility(View.GONE);

        mPaint = new Paint();
        mPaint.setTextSize(AbstractPadder.TEXT_SIZE_FOR_WIDTH_CALCULATION);
    }

    private void setChildVisibility(int v) {
        mBorderView.setVisibility(v);
    }

    @Override
    public void onDecryptOverlayLayoutParamsChanged(String packagename) {
        refreshDesignParams();
    }

    private void refreshDesignParams() {


        mShape.setStroke(mStrokePx, mColor);
        mShape.setCornerRadius(mCore.dipToPixels(mCore.getDb().getDecryptOverlayCornerRadius(mPackageName)));
    }

    public void destroy() {
        //  mCore.getDb().removeDecryptOverlayLayoutParamsChangedListener(this);

    }

    @Override
    protected WindowManager.LayoutParams createLayoutParams(Context ctx) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.packageName = ctx.getPackageName();
        layoutParams.alpha = 1;

        layoutParams.type = getOverlayType();
        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        if (!MainPreferences.INSTANCE.isAllowScreenshots(getContext())) {
            layoutParams.flags = layoutParams.flags | WindowManager.LayoutParams.FLAG_SECURE;
        }

        layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;


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


    public void onScrapeComplete(NodeTextView focusedView) {
        mFocusedNodeBounds = focusedView == null ? null : focusedView.getNodeBoundsInScreen();
        if (mFocusedNodeBounds != null && focusedView.isEditableEditText()) {
            BaseDecryptResult tdr = focusedView.getTryDecryptResult();
            boolean isEncrypted = tdr != null || focusedView.isDecrypting();
            boolean above = isEncrypted && mCore.getDb().isOverlayAboveInput(mPackageName);

            setChildVisibility(View.VISIBLE);

            ViewGroup.LayoutParams p = mBorderView.getLayoutParams();

            p.width = mFocusedNodeBounds.width() + 2 * mExtraWidthPx;
            p.height = mFocusedNodeBounds.height() + 2 * mExtraHeightPx + (above ? focusedView.getHeight() : 0);

            mBorderView.setLayoutParams(p);
            mBorderView.setX(mFocusedNodeBounds.left - mExtraWidthPx);
            mBorderView.setY(mFocusedNodeBounds.top - mExtraHeightPx - (above ? focusedView.getHeight() : 0));

            calcBorderColorBasedOnPadding(focusedView);
            refreshDesignParams();

            //check if user has corrupted inivisble part
            if (tdr == null || tdr.getError() == BaseDecryptResult.DecryptError.PROTO_ERROR) {
                if (CryptoHandlerFacade.Companion.isEncodingCorrupt(getContext(), focusedView.getOrigText())) {
                    if (!mCorruptedEncodingShownInThisSession) {
                        mCorruptedEncodingShownInThisSession = true;
                        mCore.showOverlayDialogCorruptedEncoding_UI();
                    }
                } else {
                    mCorruptedEncodingShownInThisSession = false;
                    mCore.dismissOverlayDialogCorruptedEncoding_UI();
                }
            } else {
                mCorruptedEncodingShownInThisSession = false;
                mCore.dismissOverlayDialogCorruptedEncoding_UI();
            }

        } else {
            mInsufficientPaddingShownInThisSession = false;
            setChildVisibility(View.GONE);
        }
        postInvalidate();
    }


    private void calcBorderColorBasedOnPadding(NodeTextView view) {
        int res = ContextCompat.getColor(getContext(), R.color.colorPrimary);

        String decryptedText = view.getText().toString();

        if (decryptedText.length() > 0) {

            String origText = "";
            if (view != null && view.getOrigText() != null) {
                origText = view.getOrigText();
            }

            Rect aRect = new Rect();

            mPaint.getTextBounds(origText, 0, origText.length(), aRect);
            int widthOrig = aRect.width();

            mPaint.getTextBounds(decryptedText, 0, decryptedText.length(), aRect);
            int widthActual = aRect.width();

            if (widthActual <= widthOrig) {
                res = ContextCompat.getColor(getContext(), R.color.colorOk);
                mCore.dismissOverlayDialogInsufficientPadding_UI();
            } else {
                if (!GotItPreferences.Companion.getPreferences(getContext()).isTooltipConfirmed(getContext().getString(R.string.tooltipid_edittext_insufficientpadding))) {
                    if (!mInsufficientPaddingShownInThisSession) {
                        mInsufficientPaddingShownInThisSession = true;
                        mCore.showOverlayDialogInsufficientPadding_UI();
                    }
                }
                res = ContextCompat.getColor(getContext(), R.color.colorWarning);
            }
        }
        mColor = res;
    }
}
