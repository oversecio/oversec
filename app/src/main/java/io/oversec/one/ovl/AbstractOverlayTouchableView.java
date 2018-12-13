package io.oversec.one.ovl;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import io.oversec.one.Core;

public abstract class AbstractOverlayTouchableView extends OverlayView {

    public AbstractOverlayTouchableView(Core core, String packageName) {
        super(core, packageName);
    }

    @Override
    protected WindowManager.LayoutParams createLayoutParams(Context ctx) {

        WindowManager.LayoutParams res = new WindowManager.LayoutParams(
                getMyWidth(),
                getMyHeight(),
                0,
                0,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                , PixelFormat.TRANSLUCENT);


        res.flags = res.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        res.gravity = Gravity.TOP | Gravity.LEFT;

        return res;
    }

    protected abstract int getMyWidth();

    protected abstract int getMyHeight();


}
