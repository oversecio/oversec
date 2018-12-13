package io.oversec.one.ovl;

import io.oversec.one.Core;
import io.oversec.one.R;

/**
 * Created by yao on 09/04/16.
 */
public class OverlayDialogToast extends AbstractOverlayDialogView {

    public static final long DURATION_SHORT = 1000;
    public static final long DURATION_LONG = 3000;

    private final String mText;

    public OverlayDialogToast(Core core, String packageName, String text) {
        super(core, packageName);
        mText = text;
        init();
    }

    @Override
    int getIconResId() {
        return R.drawable.ic_info_outline_black_24dp;
    }

    @Override
    String getText() {
        return mText;
    }

    @Override
    String getOkText() {
        return null;
    }

    @Override
    String getCancelText() {
        return null;
    }

    @Override
    String getNeutralText() {
        return null;
    }

    @Override
    void onOkPressed() {
        mCore.dismissOverlayToast();
    }

    @Override
    void onCancelPressed() {
    }

    @Override
    void onNeutralPressed() {
    }
}
