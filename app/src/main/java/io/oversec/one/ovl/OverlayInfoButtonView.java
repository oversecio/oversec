package io.oversec.one.ovl;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import android.widget.AbsoluteLayout;
import android.widget.ImageButton;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.ui.EncryptionInfoActivity;


public class OverlayInfoButtonView extends OverlayView {

    public static final int HEIGHT_DP = 42;
    private final ImageButton mView;
    private final int mWidth, mHeight;
    private String mEncryptedText;
    private Rect mBoundsInScreen;

    public OverlayInfoButtonView(Core core, String encryptedText, Rect boundsInScreen, String packageName) {
        super(core, packageName);
        mEncryptedText = encryptedText;
        mBoundsInScreen = new Rect(boundsInScreen);

        mHeight = core.dipToPixels(HEIGHT_DP);
        //noinspection SuspiciousNameCombination
        mWidth = mHeight;

        updateLayoutParams();

        ContextThemeWrapper ctw = new ContextThemeWrapper(core.getCtx(), R.style.AppTheme);
        mView = (ImageButton) LayoutInflater.from(ctw)
                .inflate(R.layout.overlay_info_button, null);

        //noinspection deprecation
        addView(mView, new AbsoluteLayout.LayoutParams(mWidth, mHeight, 0, 0));

        calcPosition();

        mView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCore.removeOverlayDecrypt(false);
                EncryptionInfoActivity.Companion.show(getContext(), mPackageName, mEncryptedText, mView);
            }
        });
    }

    private void calcPosition() {
        mLayoutParams.x = mBoundsInScreen.left - mWidth / 4 * 3;
        mLayoutParams.y = mBoundsInScreen.top - mHeight / 4;
        setLayoutParams(mLayoutParams);
    }


    public void updateNode(String encryptedText, Rect boundsInScreen) {
        mEncryptedText = encryptedText;
        mBoundsInScreen = new Rect(boundsInScreen);
        calcPosition();
    }


    @Override
    protected WindowManager.LayoutParams createLayoutParams(Context ctx) {

        WindowManager.LayoutParams res = new WindowManager.LayoutParams(mWidth, mHeight, getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);


        res.flags = res.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        res.x = 0;
        res.y = 0;

        res.gravity = Gravity.TOP | Gravity.LEFT;

        res.width = mWidth;
        res.height = mHeight;

        return res;
    }
}
