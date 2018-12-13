package io.oversec.one.ovl;

import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.db.Db;

public class OverlayButtonCameraView extends AbstractOverlayButtonFreeView {

    public OverlayButtonCameraView(Core core, String packageName) {
        super(core, packageName);

        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;

        mLandscapeX = mCore.getDb().getButtonCameraLandscapeX(mPackageName, 0);
        mLandscapeY = mCore.getDb().getButtonCameraLandscapeY(mPackageName, Integer.MAX_VALUE);

        mPortraitX = mCore.getDb().getButtonCameraPortraitX(mPackageName, 0);
        mPortraitY = mCore.getDb().getButtonCameraPortraitY(mPackageName, Integer.MAX_VALUE);

        mView.setImageResource(R.drawable.ic_camera_alt_black_24dp);
    }

    @Override
    void onSingleTap() {

        mCore.onButtonCameraSingleTap();
    }

    @Override
    void onLongTap() {
    }

    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonCameraLandscapeX(mPackageName, mLandscapeX);
        mCore.getDb().setButtonCameraLandscapeY(mPackageName, mLandscapeY);
        mCore.getDb().setButtonCameraPortraitX(mPackageName, mPortraitX);
        mCore.getDb().setButtonCameraPortraitY(mPackageName, mPortraitY);
    }

    public View getButtonView() {
        return mView;
    }
}
