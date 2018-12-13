package io.oversec.one.ovl;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsoluteLayout;

import io.oversec.one.Core;
import io.oversec.one.util.WrappedWindowManager;

public abstract class OverlayView extends AbsoluteLayout {

    protected final Core mCore;
    protected final WrappedWindowManager mWm;
    protected final String mPackageName;
    protected WindowManager.LayoutParams mLayoutParams;
    protected boolean mHiddenMaster;
    protected boolean mHiddenSelf;

    public OverlayView(Core core, String packageName) {
        super(core.getCtx());

        core.checkUiThread();

        //prevent from events being sent to our own scraper
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        mCore = core;
        mPackageName = packageName;
        updateLayoutParams();
        mWm = WrappedWindowManager.get(core.getCtx());
        updateVisibility();

    }

    protected abstract WindowManager.LayoutParams createLayoutParams(Context ctx);

    public void updateLayoutParams() {
        mLayoutParams = createLayoutParams(mCore.getCtx());
    }

    public WindowManager.LayoutParams getMyLayoutParams() {
        return mLayoutParams;
    }

    public void hideMaster(boolean hide) {
        mHiddenMaster = hide;
        updateVisibility();
    }

    public void hideSelf(boolean hide) {
        mHiddenSelf = hide;
        updateVisibility();
    }

    protected void updateVisibility() {
        int vis = isHidden() ? View.GONE : View.VISIBLE;
        if (vis != getVisibility()) {
            setVisibility(vis);
        }
    }

    public boolean isHidden() {
        return mHiddenMaster || mHiddenSelf || mCore.isTemporaryHidden_UI(mPackageName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + hashCode();
    }

    protected static int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        }
    }
}
