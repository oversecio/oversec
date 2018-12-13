package io.oversec.one.ovl;

import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.util.GotItPreferences;

public class OverlayDialogTooltip extends AbstractOverlayDialogView {

    public static final long DURATION_SHORT = 1000;
    public static final long DURATION_LONG = 3000;

    private final String mText;
    private final String mGotItId;
    private final String mHelpAnchor;
    private final boolean mShowEvenIfDecryptOverlayIsHidden;
    private final String mCancelText;
    private final boolean mGotItOnCancel;

    public OverlayDialogTooltip(
            Core core,
            String packageName,
            String msg,
            String gotItId,
            String helpAnchor,
            View anchor,
            boolean showEvenIfDecryptOverlayIsHidden) {
        this(core, packageName, msg, gotItId, helpAnchor, anchor, showEvenIfDecryptOverlayIsHidden, null, false);
    }

    public OverlayDialogTooltip(
            Core core,
            String packageName,
            String msg,
            String gotItId,
            String helpAnchor,
            View anchor,
            boolean showEvenIfDecryptOverlayIsHidden,
            String cancelText,
            boolean gotItOnCancel) {
        super(core, packageName, anchor);
        mText = msg;
        mGotItId = gotItId;
        mCancelText = cancelText;
        mGotItOnCancel = gotItOnCancel;
        mHelpAnchor = helpAnchor;
        mShowEvenIfDecryptOverlayIsHidden = showEvenIfDecryptOverlayIsHidden;
        init();
        updateVisibility();
    }

    @Override
    int getIconResId() {
        return R.drawable.ic_info_outline_black_24dp;
    }

    @Override
    public String getText() {
        return mText;
    }

    @Override
    String getOkText() {
        if (mGotItId != null) {
            return getContext().getString(R.string.action_gotit);
        } else {
            return getContext().getString(R.string.action_ok);
        }
    }

    @Override
    String getCancelText() {
        return mCancelText != null ? mCancelText : getContext().getString(R.string.action_moreinfo);
    }

    @Override
    String getNeutralText() {
        return null;
    }

    @Override
    void onOkPressed() {
        if (mGotItId != null) {
            GotItPreferences.Companion.getPreferences(getContext()).setTooltipConfirmed(mGotItId);
        }
        mCore.dismissOverlayTooltip();
    }

    @Override
    void onCancelPressed() {
        Help.INSTANCE.open(getContext(), mHelpAnchor);
        if (mGotItOnCancel) {
            onOkPressed();
        }
    }

    @Override
    void onNeutralPressed() {
    }

    @Override
    public boolean isHidden() {
        if (mShowEvenIfDecryptOverlayIsHidden) {
            return false;
        }
        return super.isHidden();
    }

    public String getGotItId() {
        return mGotItId;
    }
}
