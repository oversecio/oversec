package io.oversec.one.ovl;

import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.util.GotItPreferences;

public class OverlayDialogInsufficientPaddingView extends AbstractOverlayDialogView {

    final static String PREF_KEY = "toast_insufficient_padding_3";

    public OverlayDialogInsufficientPaddingView(Core core, String packageName, View anchor) {
        super(core, packageName, anchor);
        init();
    }

    @Override
    int getIconResId() {
        return R.drawable.ic_warning_black_24dp;
    }

    @Override
    String getText() {
        return getResources().getString(R.string.toast_insufficient_padding);
    }

    @Override
    String getOkText() {
        return getResources().getString(R.string.action_gotit);
    }

    @Override
    String getCancelText() {
        return getResources().getString(R.string.action_dismiss);
    }

    @Override
    String getNeutralText() {
        return getResources().getString(R.string.action_moreinfo);
    }

    @Override
    void onOkPressed() {
        mCore.dismissOverlayDialogInsufficientPadding_UI();
        GotItPreferences.Companion.getPreferences(getContext()).setTooltipConfirmed(getContext().getString(R.string.tooltipid_edittext_insufficientpadding));
    }

    @Override
    void onCancelPressed() {
        mCore.dismissOverlayDialogInsufficientPadding_UI();
    }

    @Override
    void onNeutralPressed() {
        Help.INSTANCE.open(getContext(), Help.ANCHOR.input_insufficientpadding);
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView) {
        mAnchor = focusedView;
        super.onScrapeComplete(focusedView);
    }

    @Override
    protected boolean forceOnTop() {
        return true;
    }
}
