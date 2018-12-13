package io.oversec.one.util;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import io.oversec.one.ovl.OverlayView;
import io.oversec.one.ui.ManageOverlayPermissionWarningActivity;

public class WrappedWindowManager {
    private static WrappedWindowManager INSTANCE;
    private final WindowManager mWm;
    private final Context mCtx;

    private WrappedWindowManager(Context ctx) {
        mCtx = ctx;
        mWm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized WrappedWindowManager get(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new WrappedWindowManager(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    public Display getDefaultDisplay() {
        return mWm.getDefaultDisplay();
    }

    public void updateViewLayout(View v, ViewGroup.LayoutParams params) {
        try {
            mWm.updateViewLayout(v, params);
        } catch (Exception ex) {
            checkOrShowManageOverlayPermissionIntent(mCtx);
        }
    }

    public static void checkOrShowManageOverlayPermissionIntent(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(ctx)) {
                ManageOverlayPermissionWarningActivity.show(ctx);
            }
        }
    }

    public void removeView(View v) {
        try {
            mWm.removeView(v);
        } catch (Exception ex) {
            checkOrShowManageOverlayPermissionIntent(mCtx);
        }
    }

    public void addView(OverlayView v, ViewGroup.LayoutParams params) {
        try {
            mWm.addView(v, params);
        } catch (Exception ex) {
            checkOrShowManageOverlayPermissionIntent(mCtx);
        }
    }
}
