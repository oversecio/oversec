package io.oversec.one.ovl;

import android.support.v4.content.ContextCompat;

import io.oversec.one.Core;
import io.oversec.one.db.Db;

import io.oversec.one.R;

public class OverlayButtonInfomodeView extends AbstractOverlayButtonFreeView {

    public OverlayButtonInfomodeView(Core core, String packageName) {
        super(core, packageName);

        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;

        mLandscapeX = mCore.getDb().getButtonInfomodeLandscapeX(mPackageName, 0);
        mLandscapeY = mCore.getDb().getButtonInfomodeLandscapeY(mPackageName, myWH * 2);

        mPortraitX = mCore.getDb().getButtonInfomodePortraitX(mPackageName, 0);
        mPortraitY = mCore.getDb().getButtonInfomodePortraitY(mPackageName, myWH * 2);

        mView.setImageResource(R.drawable.ic_info_outline_black_24dp);
    }

    @Override
    void onSingleTap() {

        mCore.onButtonInfomodeSingleTap();
    }

    @Override
    void onLongTap() {
        mCore.onButtonInfomodeLongTap();
    }

    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonInfomodeLandscapeX(mPackageName, mLandscapeX);
        mCore.getDb().setButtonInfomodeLandscapeY(mPackageName, mLandscapeY);
        mCore.getDb().setButtonInfomodePortraitX(mPackageName, mPortraitX);
        mCore.getDb().setButtonInfomodePortraitY(mPackageName, mPortraitY);
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes) {
        super.onScrapeComplete(focusedView, hasVisibleNodes);

        //TODO: maybe make this configurable
        hideSelf(!hasVisibleNodes);

        if (mCore.isInfoMode()) {
            mView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_clear_black_24dp));
        } else {
            mView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_info_outline_black_24dp));
        }
    }

}
