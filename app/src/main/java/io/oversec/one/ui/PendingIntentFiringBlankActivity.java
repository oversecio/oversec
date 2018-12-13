package io.oversec.one.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

import io.oversec.one.Core;

import io.oversec.one.acs.OversecAccessibilityService;
import roboguice.util.Ln;

public class PendingIntentFiringBlankActivity extends Activity {
    private static final String EXTRA_PENDING_INTENT = "pending_intent";
    private static final String EXTRA_REQUEST_CODE = "request_code";
    private static long mShowing;
    private static PendingIntentResultCallback mCallback;

    public static synchronized void fire(Context ctx, PendingIntent pi, int requestCode, PendingIntentResultCallback callback) {
        if (isShowing()) {
            return;
        }
        mShowing = System.currentTimeMillis();
        mCallback = callback;

        Intent i = makeIntent(ctx, pi, requestCode);

        OversecAccessibilityService acs = Core.getInstance(ctx).getAcs();
        if (acs != null) {
            acs.clearMainHandler();
        }

        ctx.startActivity(i);
    }

    public static boolean isShowing() {
        //There is REALLY no way to determine if the activity started by the pending intent went to background,
        //i.e. the user navigated aways from it.
        //so we treat it as showing for 10 seconds,
        //which will at least prevent any scrape loops to fire it up too frequently

        //Note that this is merely an optimization to prevent multiple creation /destruction , so
        //nothing will break if this interval is no adequate, stuff will just be slower!
        boolean res = mShowing > 0 && (System.currentTimeMillis() - mShowing) < (10000);
        return res;
    }

    private static synchronized Intent makeIntent(Context ctx, PendingIntent pi, int requestCode) {

        Intent i = new Intent();
        i.setClass(ctx, PendingIntentFiringBlankActivity.class);
        i.putExtra(EXTRA_PENDING_INTENT, pi);


        i.putExtra(EXTRA_REQUEST_CODE, requestCode);
        i.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        );
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PendingIntent pi = getIntent().getParcelableExtra(EXTRA_PENDING_INTENT);
        int requestCode = getIntent().getIntExtra(EXTRA_REQUEST_CODE, 0);

        try {
            int flagsMask = 0;//Intent.FLAG_ACTIVITY_NO_USER_ACTION;
            int flagsValues = 0;//Intent.FLAG_ACTIVITY_NO_USER_ACTION;

            startIntentSenderForResult(pi.getIntentSender(), requestCode, null, flagsMask, flagsValues, 0);
        } catch (IntentSender.SendIntentException e) {
            onActivityResult(requestCode, 0, null);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        mShowing = 0;
        finish();

        if (mCallback != null) {
            final PendingIntentResultCallback aTempCallback = mCallback;
            Core.getInstance(this).postRunnableOnUiThread(new Runnable() {
                @Override
                public void run() {
                    aTempCallback.onResultFromPendingIntentActivity(requestCode, resultCode, data);
                }
            });
            mCallback = null;
        } else {
            // Ln.w("No ActivityResultHandler for requestCode %s", requestCode);
        }
    }

    public interface PendingIntentResultCallback {
        void onResultFromPendingIntentActivity(int requestCode, int resultCode, Intent data);
    }
}
