package io.oversec.one.ovl;

import android.graphics.drawable.Drawable;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.db.Db;

public class OverlayButtonHideView extends AbstractOverlayButtonFreeView {

    private final Drawable mVisibleBackground;

    public OverlayButtonHideView(Core core, String packageName) {
        super(core, packageName);

        int maxDim = Math.max(mDisplayWidth, mDisplayHeight);
        int minDim = Math.min(mDisplayWidth, mDisplayHeight);

        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;

        mLandscapeX = mCore.getDb().getButtonHideLandscapeX(mPackageName, maxDim - myWH);
        mLandscapeY = mCore.getDb().getButtonHideLandscapeY(mPackageName, STATUSBAR_PLUS_ACTIONBAR_HEIGHT_PX);

        mPortraitX = mCore.getDb().getButtonHidePortraitX(mPackageName, minDim - myWH);
        mPortraitY = mCore.getDb().getButtonHidePortraitY(mPackageName, STATUSBAR_PLUS_ACTIONBAR_HEIGHT_PX);

        setImage();

        mVisibleBackground = mView.getBackground();
    }

    private void setImage() {
        mView.setImageResource(R.drawable.ic_not_interested_black_24dp);
    }

    @Override
    void onSingleTap() {
        mCore.onButtonHideSingleTap();
    }

    @Override
    void onLongTap() {
        mCore.onButtonHideLongTap();
    }

    @Override
    void onDoubleTap() {
        mCore.onButtonHideDoubleTap();
    }


    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonHideLandscapeX(mPackageName, mLandscapeX);
        mCore.getDb().setButtonHideLandscapeY(mPackageName, mLandscapeY);
        mCore.getDb().setButtonHidePortraitX(mPackageName, mPortraitX);
        mCore.getDb().setButtonHidePortraitY(mPackageName, mPortraitY);
    }

    public void hideButton(boolean hidden) {
        if (hidden) {
            mView.setBackground(null);
            mView.setImageDrawable(null);
        } else {
            mView.setBackground(mVisibleBackground);
            setImage();
        }
    }

    @Override
    public boolean isHidden() {
        return mHiddenMaster || mHiddenSelf;
    }

}
