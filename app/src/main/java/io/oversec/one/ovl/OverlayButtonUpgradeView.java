package io.oversec.one.ovl;

import android.support.v4.content.res.ResourcesCompat;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.db.Db;
import io.oversec.one.iab.IabUtil;
import roboguice.util.Ln;

public class OverlayButtonUpgradeView extends AbstractOverlayButtonFreeView {

    public OverlayButtonUpgradeView(Core core, String packageName) {
        super(core, packageName);

        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;

        mPortraitX = mDisplayWidth / 2 - myWH / 2;
        mPortraitY = mDisplayHeight / 2 - myWH / 2;

        //noinspection SuspiciousNameCombination
        mLandscapeX = mPortraitY;
        //noinspection SuspiciousNameCombination
        mLandscapeY = mPortraitX;


        mView.setImageDrawable(null);
        mView.setBackground(null);

        mView.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    mView.setImageResource(R.drawable.ic_shop_black_24dp);
                    mView.setBackground(
                            ResourcesCompat.getDrawable(getResources(), R.drawable.fab_bg, getContext().getTheme()));
                } catch (Exception ex) {

                }
            }
        }, IabUtil.getInstance(getContext()).getUpgradeButtonDelay(mView.getContext()));
    }

    @Override
    protected void setButtonColor() {
        //reset the buttons bg to the default!
        mView.setBackground(
                ResourcesCompat.getDrawable(getResources(), R.drawable.fab_bg, getContext().getTheme()));
    }

    @Override
    void onSingleTap() {
        mCore.onButtonUpgradeSingleTap();
    }

    @Override
    void onLongTap() {
    }

    @Override
    protected void store(Db db) {
        //NO store, alyway show in the middle of the screen
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes) {
        super.onScrapeComplete(focusedView, hasVisibleNodes);
    }
}
