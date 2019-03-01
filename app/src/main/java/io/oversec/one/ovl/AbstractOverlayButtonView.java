package io.oversec.one.ovl;

import android.animation.LayoutTransition;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsoluteLayout;
import android.widget.ImageButton;
import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.db.Db;
import roboguice.util.Ln;

public abstract class AbstractOverlayButtonView extends AbstractOverlayTouchableView implements Handler.Callback {
    static final int WH_DP = 56;
    static final int WH_SMALL_DP = 42;
    static final int STATUSBAR_PLUS_ACTIONBAR_HEIGHT_DP = 70;
    static final int ENCRYPT_DECRYPT_PADDING_DP = 0;

    static int WH_PX;
    static int WH_SMALL_PX;
    static int STATUSBAR_PLUS_ACTIONBAR_HEIGHT_PX;
    static int ENCRYPT_DECRYPT_PADDING_PX;

    private static final long LONG_TAP_DELAY = 500;
    private static final long DOUBLE_TAP_DELAY = 250;

    private static final int WHAT_LONG_TAP = 1;
    private static final int WHAT_SINGLE_TAP = 2;
    private static final int WHAT_STORE = 3;


    protected static final HandlerThread mHANDLER_THREAD = new HandlerThread("BUTTON");

    static {
        mHANDLER_THREAD.start();
    }


    protected final Handler mHandler;
    protected final ImageButton mView;
    private GradientDrawable mButtonFront = null;

    protected int mDragThresholdPixels;
    protected TouchInfo touchInfo = new TouchInfo();

    protected IDragListener mDragListener;

    protected int mOrientation;
    protected int mDisplayWidth, mDisplayHeight;


    public AbstractOverlayButtonView(Core core, String packageName) {
        super(core, packageName);

        WH_PX = mCore.dipToPixels(WH_DP);
        WH_SMALL_PX = mCore.dipToPixels(WH_SMALL_DP);
        STATUSBAR_PLUS_ACTIONBAR_HEIGHT_PX = mCore.dipToPixels(STATUSBAR_PLUS_ACTIONBAR_HEIGHT_DP);
        ENCRYPT_DECRYPT_PADDING_PX = mCore.dipToPixels(ENCRYPT_DECRYPT_PADDING_DP);

        mOrientation = mCore.getOrientation_UI();
        setLayoutTransition(new LayoutTransition());
        mDragThresholdPixels = core.dipToPixels(5);

        ContextThemeWrapper ctw = new ContextThemeWrapper(core.getCtx(), R.style.AppTheme);
        mView = (ImageButton) LayoutInflater.from(ctw)
                .inflate(R.layout.overlay_button, null);

        LayerDrawable ld = (LayerDrawable) mView.getBackground();
        Drawable front = ld.getDrawable(1);

        if (front instanceof GradientDrawable) {
            mButtonFront = (GradientDrawable) front;
        } else if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && front instanceof RippleDrawable) {
            RippleDrawable rd = (RippleDrawable) front;
            mButtonFront = (GradientDrawable) rd.getDrawable(1);
        }

        setButtonColor();


        int myWH = isSmall() ? WH_SMALL_PX : WH_PX;
        //noinspection deprecation
        addView(mView, new AbsoluteLayout.LayoutParams(myWH, myWH, 0, 0));

        updateLayoutParams();

        mView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return onTouchHandleMove(event);
            }
        });


        mHandler = new Handler(mHANDLER_THREAD.getLooper(), this);


        onOrientationChanged(getResources().getConfiguration().orientation, false);

    }

    protected void setButtonColor() {
        if (mButtonFront != null) {
            mButtonFront.setColor(mCore.getDb().getButtonOverlayBgColor(mPackageName));
        }
    }


    @Override
    public boolean requestSendAccessibilityEvent(View view,
                                                 AccessibilityEvent event) {
        // Never send accessibility events.
        return false;
    }


    protected boolean isSmall() {
        return false;
    }


    protected int getMyWidth() {
        return isSmall() ? WH_SMALL_PX : WH_PX;
    }

    protected int getMyHeight() {
        return getMyWidth();
    }

    public boolean onTouchHandleMove(
            MotionEvent event) {

        int totalDeltaX = touchInfo.lastX - touchInfo.firstX;
        int totalDeltaY = touchInfo.lastY - touchInfo.firstY;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchInfo.lastX = (int) event.getRawX();
                touchInfo.lastY = (int) event.getRawY();

                touchInfo.firstX = touchInfo.lastX;
                touchInfo.firstY = touchInfo.lastY;

                mHandler.sendEmptyMessageDelayed(WHAT_LONG_TAP, LONG_TAP_DELAY);

                break;
            case MotionEvent.ACTION_MOVE:

                int deltaX = (int) event.getRawX() - touchInfo.lastX;
                int deltaY = (int) event.getRawY() - touchInfo.lastY;

                touchInfo.lastX = (int) event.getRawX();
                touchInfo.lastY = (int) event.getRawY();

                if (touchInfo.moving
                        || Math.abs(totalDeltaX) >= mDragThresholdPixels
                        || Math.abs(totalDeltaY) >= mDragThresholdPixels) {
                    touchInfo.moving = true;
                    mHandler.removeMessages(WHAT_LONG_TAP);
                    mHandler.removeMessages(WHAT_SINGLE_TAP);

                    if (mCore.isTemporaryHidden_UI(mPackageName)) {
                        return  true;
                    }

                    // update the position of the window
                    if (event.getPointerCount() == 1) {
                        mLayoutParams.x += deltaX;
                        mLayoutParams.y += deltaY;
                    }

                    validatePosition(mLayoutParams);

                    setPosition(mLayoutParams.x, mLayoutParams.y);

                    storeTransientPosition(mLayoutParams.x, mLayoutParams.y);


                    mHandler.removeMessages(WHAT_STORE);
                    mHandler.sendEmptyMessageDelayed(WHAT_STORE, 300);


                    if (mDragListener != null) {
                        mDragListener.onDrag(mLayoutParams.x, mLayoutParams.y);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeMessages(WHAT_LONG_TAP);
                if (!touchInfo.moving) {

                    boolean tap = Math.abs(totalDeltaX) < mDragThresholdPixels
                            && Math.abs(totalDeltaY) < mDragThresholdPixels;

                    if (tap) {

                        if (event.getEventTime() - touchInfo.lastUp < DOUBLE_TAP_DELAY) {
                            mHandler.removeMessages(WHAT_SINGLE_TAP);
                            onDoubleTap();
                        } else {
                            mHandler.sendEmptyMessageDelayed(WHAT_SINGLE_TAP, DOUBLE_TAP_DELAY);
                        }

                    }
                }
                touchInfo.moving = false;
                touchInfo.lastUp = event.getEventTime();
                break;
        }


        return true;
    }

    protected void validatePosition(WindowManager.LayoutParams layoutParams) {

    }

    protected abstract void storeTransientPosition(int x, int y);

    protected boolean isImeFullScreen() {
        boolean res = mCore.isImeFullscreen();
        Ln.d("PLOUGH: isImeFullScreen = %s ", res);
        return res;

    }


    protected void setPosition(int x, int y) {
        // keep window inside edges
        mLayoutParams.x = Math.min(Math.max(x, 0), mDisplayWidth
                - mLayoutParams.width);
        mLayoutParams.y = Math.min(Math.max(y, 0), mDisplayHeight
                - mLayoutParams.height);


       mWm.updateViewLayout(AbstractOverlayButtonView.this, mLayoutParams);

    }

    public abstract void onScrapeComplete(NodeTextView focusedView, boolean hasVisibleNodes);

    public void onOrientationChanged(int orientation, boolean updatePosition) {
        mOrientation = orientation;
        Display display = mWm.getDefaultDisplay();
        mDisplayWidth = display.getWidth();
        mDisplayHeight = display.getHeight();

        if (updatePosition) {
            updatePosition();
        }
    }

    protected abstract void updatePosition();


    class TouchInfo {
        /**
         * The state of the window.
         */
        public int firstX, firstY, lastX, lastY;


        /**
         * Whether we're past the move threshold already.
         */
        public boolean moving;

        public long lastUp;
    }

    @Override
    public boolean handleMessage(Message msg) {
        int what = msg.what;
        switch (what) {
            case WHAT_LONG_TAP:
                onLongTap();
                return true;
            case WHAT_SINGLE_TAP:
                onSingleTap();
                return true;
            case WHAT_STORE:
                store(mCore.getDb());
                return true;
        }
        return false;
    }

    protected abstract void store(Db db);

    abstract void onSingleTap();

    abstract void onLongTap();

    void onDoubleTap() {
        Ln.d("DOUBLE TAP!!!");
    }
}
