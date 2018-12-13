package io.oversec.one.ovl;

import android.content.res.Configuration;

import io.oversec.one.Core;

public abstract class AbstractOverlayButtonFreeView extends AbstractOverlayButtonView {
    protected int mLandscapeX, mLandscapeY;
    protected int mPortraitX, mPortraitY;

    public AbstractOverlayButtonFreeView(Core core, String packageName) {
        super(core, packageName);
    }


    @Override
    protected void storeTransientPosition(int x, int y) {
        if (Configuration.ORIENTATION_PORTRAIT == mOrientation) {
            mPortraitX = mLayoutParams.x;
            mPortraitY = mLayoutParams.y;
        } else {
            mLandscapeX = mLayoutParams.x;
            mLandscapeY = mLayoutParams.y;
        }
    }

    @Override
    public void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes) {

        if (isImeFullScreen()) {
            hideSelf(true);
        } else {
            hideSelf(false);
            updatePosition();
        }

    }

    @Override
    protected void updatePosition() {
        setPosition(mOrientation == Configuration.ORIENTATION_PORTRAIT ? mPortraitX : mLandscapeX,
                mOrientation == Configuration.ORIENTATION_PORTRAIT ? mPortraitY : mLandscapeY);
    }
}
