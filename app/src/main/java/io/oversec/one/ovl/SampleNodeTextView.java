package io.oversec.one.ovl;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import io.oversec.one.Core;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;
import io.oversec.one.db.Db;

public class SampleNodeTextView extends TextView implements IDecryptOverlayLayoutParamsChangedListener {
    private GradientDrawable mBgShape;
    private Db mDb;
    private Core mCore;

    public SampleNodeTextView(Context context) {
        super(context);
        setUp();
    }

    public SampleNodeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp();
    }

    public SampleNodeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setUp();
    }

    private void setUp() {
        if (!isInEditMode()) {
            mCore = Core.getInstance(getContext());
            mDb = mCore.getDb();
        }

        mBgShape = new GradientDrawable();
        setBackground(mBgShape);
    }

    @Override
    public void onDecryptOverlayLayoutParamsChanged(String packagename) {
        mBgShape.setCornerRadius(mCore.dipToPixels(mDb.getDecryptOverlayCornerRadius(packagename)));
        mBgShape.setColor(mDb.getDecryptOverlayBgColor(packagename));

        setTextColor(mDb.getDecryptOverlayTextColor(packagename));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 6 + mDb.getDecryptOverlayTextSize(packagename));

        int statusIconResId = 0;
        if (mDb.isShowStatusIcon(packagename)) {
            statusIconResId = io.oversec.one.crypto.R.drawable.ic_done_all_green_a700_18dp;
        }
        setCompoundDrawablesWithIntrinsicBounds(0, 0, statusIconResId, 0);

        int paddingLeft = mCore.dipToPixels(mDb.getDecryptOverlayPaddingLeft(packagename));
        int paddingTop = mCore.dipToPixels(mDb.getDecryptOverlayPaddingTop(packagename));

        setPadding(paddingLeft, paddingTop, 0, 0);
    }
}
