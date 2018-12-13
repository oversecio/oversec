package io.oversec.one.ovl;

import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.Help;

public class OverlayDialogCorruptedEncodingView extends AbstractOverlayDialogView {

    public OverlayDialogCorruptedEncodingView(Core core, String packageName, View anchor) {
        super(core, packageName, anchor);
        init();
    }

    @Override
    int getIconResId() {
        return R.drawable.ic_warning_black_24dp;
    }

    @Override
    String getText() {
        return getResources().getString(R.string.toast_corrupted_encoding);
    }

    @Override
    String getOkText() {
        return getResources().getString(R.string.action_clear_input);
    }

    @Override
    String getCancelText() {
        return null;
    }

    @Override
    String getNeutralText() {
        return getResources().getString(R.string.action_moreinfo);
    }

    @Override
    void onOkPressed() {
        mCore.dismissOverlayDialogCorruptedEncoding_UI();
        mCore.clearCurrentFocusedEditText_UI();
    }

    @Override
    void onCancelPressed() {
    }

    @Override
    void onNeutralPressed() {
        Help.INSTANCE.open(getContext(), Help.ANCHOR.input_corruptedencoding);
    }

    @Override
    protected boolean forceOnTop() {
        return true;
    }

    protected int getExtraToolTipHeight() {
        return mCore.dipToPixels(20);
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView) {
        mAnchor = focusedView;
        super.onScrapeComplete(focusedView);
    }
}
