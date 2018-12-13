package io.oversec.one.ovl;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.iab.IabUtil;

public class OverlayDialogUpgrade extends AbstractOverlayDialogView {

    public OverlayDialogUpgrade(Core core) {
        super(core, null, null);
        init();
    }

    @Override
    int getIconResId() {
        return R.drawable.ic_shop_black_24dp;
    }

    @Override
    String getText() {
        return getResources().getString(R.string.upgrade_overlay_msg);
    }

    @Override
    String getOkText() {
        return getResources().getString(R.string.action_upgrade);
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
        mCore.removeUpgradeDialog();
        IabUtil.getInstance(getContext()).showPurchaseActivity(getContext());
    }

    @Override
    void onCancelPressed() {
        mCore.removeUpgradeDialog_UI();
    }

    @Override
    void onNeutralPressed() {
    }
}
