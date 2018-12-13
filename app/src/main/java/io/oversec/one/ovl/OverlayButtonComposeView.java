package io.oversec.one.ovl;

import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.Help;
import io.oversec.one.db.Db;

public class OverlayButtonComposeView extends AbstractOverlayButtonFreeView {

    public OverlayButtonComposeView(Core core, String packageName) {
        super(core, packageName);

        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;

        mLandscapeX = mCore.getDb().getButtonComposeLandscapeX(mPackageName, 0);
        mLandscapeY = mCore.getDb().getButtonComposeLandscapeY(mPackageName, Integer.MAX_VALUE);

        mPortraitX = mCore.getDb().getButtonComposePortraitX(mPackageName, 0);
        mPortraitY = mCore.getDb().getButtonComposePortraitY(mPackageName, Integer.MAX_VALUE);

        mView.setImageResource(R.drawable.ic_create_black_24dp);
    }

    @Override
    void onSingleTap() {

        mCore.onButtonComposeSingleTap();
    }

    @Override
    void onLongTap() {
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes) {
        super.onScrapeComplete(focusedView, hasVisibleNodes);
        if (!isHidden()) {
            mCore.showTooltip_UI(this,
                    mCore.getCtx().getString(R.string.tooltip_buttoncompose),
                    getContext().getString(R.string.tooltipid_buttoncompose),
                    Help.ANCHOR.button_compose, false);
        }
    }

    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonComposeLandscapeX(mPackageName, mLandscapeX);
        mCore.getDb().setButtonComposeLandscapeY(mPackageName, mLandscapeY);
        mCore.getDb().setButtonComposePortraitX(mPackageName, mPortraitX);
        mCore.getDb().setButtonComposePortraitY(mPackageName, mPortraitY);

    }

    public View getButtonView() {
        return mView;
    }
}
