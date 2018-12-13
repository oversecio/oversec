package io.oversec.one.ovl;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;

import io.oversec.one.Core;

public class OverlayOutsideTouchView extends OverlayView {

    public OverlayOutsideTouchView(Core core, String packagename) {
        super(core, packagename);
    }

    @Override
    protected WindowManager.LayoutParams createLayoutParams(Context ctx) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();

        layoutParams.packageName = ctx.getPackageName();
        layoutParams.alpha = 1;
        layoutParams.type = getOverlayType();
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        layoutParams.gravity = Gravity.CENTER;
        layoutParams.format = PixelFormat.TRANSLUCENT;

        layoutParams.x = 0;
        layoutParams.y = 0;
        layoutParams.width = 0;
        layoutParams.height = 0;

        return layoutParams;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mCore.startPreemptiveRefresh_UI();
        }
        return false;
    }

}
