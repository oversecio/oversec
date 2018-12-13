package io.oversec.one.ovl;

import io.oversec.one.Core;
import io.oversec.one.R;

public class OverlayDialogUserInteractionRequired extends AbstractOverlayDialogView {

    private final String mEncodedText;

    public OverlayDialogUserInteractionRequired(Core core, String packageName, String encodedText) {
        super(core, packageName, null);
        mEncodedText = encodedText;
        init();
    }

    @Override
    int getIconResId() {
        return R.drawable.ic_warning_black_24dp;
    }

    @Override
    String getText() {
        return getResources().getString(R.string.decrypt_error_user_interaction_required__long);
    }

    @Override
    String getOkText() {
        return getResources().getString(R.string.action_ok);
    }

    @Override
    String getCancelText() {
        return getResources().getString(R.string.action_cancel);
    }

    @Override
    String getNeutralText() {
        return null;
    }

    @Override
    void onOkPressed() {
        mCore.onUserInteractionDialogConfirmed_UI(true, mEncodedText);
    }

    @Override
    void onCancelPressed() {
        mCore.onUserInteractionDialogConfirmed_UI(false, mEncodedText);
    }

    @Override
    void onNeutralPressed() {
    }
}
