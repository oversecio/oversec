package io.oversec.one.ovl;

import android.view.View;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.db.Db;

public class OverlayButtonConfigView extends AbstractOverlayButtonFreeView {

    public OverlayButtonConfigView(Core core, String packageName) {
        super(core, packageName);

        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;

        mLandscapeX = mCore.getDb().getButtonConfigLandscapeX(mPackageName, 0);
        mLandscapeY = mCore.getDb().getButtonConfigLandscapeY(mPackageName, STATUSBAR_PLUS_ACTIONBAR_HEIGHT_PX);

        mPortraitX = mCore.getDb().getButtonConfigPortraitX(mPackageName, 0);
        mPortraitY = mCore.getDb().getButtonConfigPortraitY(mPackageName, STATUSBAR_PLUS_ACTIONBAR_HEIGHT_PX);

        mView.setImageResource(R.drawable.ic_settings_black_24dp);
    }

    @Override
    void onSingleTap() {

        mCore.onButtonConfigSingleTap();
    }

    @Override
    void onLongTap() {
        mCore.onButtonConfigLongTap();
    }

    @Override
    protected void store(Db db) {
        mCore.getDb().setButtonConfigLandscapeX(mPackageName, mLandscapeX);
        mCore.getDb().setButtonConfigLandscapeY(mPackageName, mLandscapeY);
        mCore.getDb().setButtonConfigPortraitX(mPackageName, mPortraitX);
        mCore.getDb().setButtonConfigPortraitY(mPackageName, mPortraitY);

    }

    public View getButtonView() {
        return mView;
    }
}
