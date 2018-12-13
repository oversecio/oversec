package io.oversec.one.ovl;

import android.content.Context;
import android.graphics.Outline;
import android.os.Build;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.ui.util.StandaloneTooltipView;
import io.oversec.one.crypto.ui.util.TooltipBackgroundDrawable;

public abstract class AbstractOverlayDialogView extends AbstractOverlayTouchableView {

    private final ViewGroup mView;
    private final Button mBtCancel;
    private final Button mBtOk;
    private final Button mBtNeutral;
    private final TextView mTvMsg;
    protected final ViewGroup mCustomContainer;
    protected View mAnchor;
    private final TooltipBackgroundDrawable mBgDrawable;
    private final int mPadding;
    private int mOrientation;
    private int mDisplayWidth, mDisplayHeight;


    public AbstractOverlayDialogView(Core core, String packageName) {
        this(core, packageName, null);
    }

    public AbstractOverlayDialogView(Core core, String packageName, View anchor) {
        super(core, packageName);

        mAnchor = anchor;
        updateLayoutParams();

        ContextThemeWrapper ctw = new ContextThemeWrapper(core.getCtx(), R.style.AppTheme);
        mView = (ViewGroup) LayoutInflater.from(ctw)
                .inflate(R.layout.overlay_dialog, null);


        mBgDrawable = new TooltipBackgroundDrawable(core.getCtx());
        mPadding = core.dipToPixels(StandaloneTooltipView.DEFAULT_PADDING_DP);


        mView.findViewById(R.id.content).setBackground(mBgDrawable);
        //   mView.findViewById(R.id.content).setOutlineProvider();


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewOutlineProvider viewOutlineProvider = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        outline.setRect(mPadding, mPadding, view.getWidth() - mPadding, view.getHeight() - mPadding);
                    }
                }
            };
            mView.findViewById(R.id.content).setOutlineProvider(viewOutlineProvider);
            mView.setElevation(mCore.dipToPixels(16));
        }

        addView(mView, new LayoutParams(getMyWidth(), getMyHeight(), 0, 0));

        mTvMsg = (TextView) mView.findViewById(R.id.text);
        mBtOk = (Button) mView.findViewById(R.id.buttonOK);
        mBtNeutral = (Button) mView.findViewById(R.id.buttonNeutral);
        mBtCancel = (Button) mView.findViewById(R.id.buttonCancel);
        mCustomContainer = (ViewGroup) mView.findViewById(R.id.custom_container);


        onOrientationChanged(getResources().getConfiguration().orientation, false);
    }

    protected void init() {

        mTvMsg.setText(getText());
        mTvMsg.setCompoundDrawablesWithIntrinsicBounds(getIconResId(), 0, 0, 0);

        if (getOkText() != null) {
            mBtOk.setText(getOkText());
        } else {
            mBtOk.setVisibility(View.GONE);
        }

        if (getCancelText() != null) {
            mBtCancel.setText(getCancelText());
        } else {
            mBtCancel.setVisibility(View.GONE);
        }

        if (getNeutralText() != null) {
            mBtNeutral.setText(getNeutralText());
        } else {
            mBtNeutral.setVisibility(View.GONE);
        }

        mBtOk.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onOkPressed();
            }
        });

        mBtCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onCancelPressed();
            }
        });

        mBtNeutral.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onNeutralPressed();
            }
        });
    }

    @Override
    protected WindowManager.LayoutParams createLayoutParams(Context ctx) {
        WindowManager.LayoutParams r = super.createLayoutParams(ctx);
        if (mAnchor == null) {
            r.gravity = Gravity.CENTER;
        } else {
            //should be handled through updatePosition
        }
        return r;
    }

    public void onOrientationChanged(int orientation, boolean updatePosition) {
        mOrientation = orientation;
        Display display = mWm.getDefaultDisplay();
        mDisplayWidth = display.getWidth();
        mDisplayHeight = display.getHeight();

        if (updatePosition) {
            updatePosition();
        }
    }


    public void updatePosition() {

        if (mAnchor == null) {
            mBgDrawable.setAnchor(TooltipBackgroundDrawable.ARROW_SIDE.NONE, mPadding, 50);
            return;
        }
        int[] loc = new int[2];
        mAnchor.getLocationOnScreen(loc);


        int sx = loc[0] + mAnchor.getWidth() / 2;
        int sy = loc[1];

        int y = sy;
        TooltipBackgroundDrawable.ARROW_SIDE side = TooltipBackgroundDrawable.ARROW_SIDE.BOTTOM;
        if (sy > mDisplayHeight / 2 || forceOnTop()) {
            y = sy - getHeight() + getExtraToolTipHeight();
            side = TooltipBackgroundDrawable.ARROW_SIDE.BOTTOM;
        } else {
            y = sy + mAnchor.getHeight();
            side = TooltipBackgroundDrawable.ARROW_SIDE.TOP;
        }

        int x = sx - getWidth() / 2; //ideally we'd have the arrow in the middle

        setPosition(x, y); //this might adjust the coordinates to keep the view on screen!

        int rx = mLayoutParams.x;
        int ry = mLayoutParams.y;

        int xRelativeToOurLeft = sx - rx;
        int ourWidth = getWidth();
        int w = ourWidth == 0 ? 50 : (int) ((double) xRelativeToOurLeft / (double) ourWidth * 100);
        w = Math.min(95, Math.max(5, w));

        mBgDrawable.setAnchor(side, mPadding, w);
    }

    protected int getExtraToolTipHeight() {
        return 0;
    }


    protected boolean forceOnTop() {
        return false;
    }

    protected void setPosition(int x, int y) {
        // keep window inside edges
        mLayoutParams.x = Math.min(Math.max(x, 0), mDisplayWidth
                - getWidth());
        mLayoutParams.y = Math.min(Math.max(y, 0), mDisplayHeight
                - getHeight());


        mWm.updateViewLayout(AbstractOverlayDialogView.this, mLayoutParams);

    }


    public void onScrapeComplete(NodeTextView focusedView) {
        updatePosition();
    }


    @Override
    protected int getMyWidth() {
        return LayoutParams.WRAP_CONTENT;
    }

    @Override
    protected int getMyHeight() {
        return LayoutParams.WRAP_CONTENT;
    }

    abstract int getIconResId();

    abstract String getText();

    abstract String getOkText();

    abstract String getCancelText();

    abstract String getNeutralText();

    abstract void onOkPressed();

    abstract void onCancelPressed();

    abstract void onNeutralPressed();

}
