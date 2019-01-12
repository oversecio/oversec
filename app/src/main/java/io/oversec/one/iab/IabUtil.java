package io.oversec.one.iab;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.oversec.one.BuildConfig;
import io.oversec.one.R;
import roboguice.util.Ln;

public class IabUtil extends BroadcastReceiver implements IabHelper.OnIabSetupFinishedListener {
    private static final String BASE64PUBLICKEY = BuildConfig.GOOGLE_PLAY_PUBKEY;


    public static final List<String> SKUS_FULL_VERSION = Arrays.asList(
//            "android.test.purchased",
//            "android.test.canceled",
//            "android.test.refunded",
//            "android.test.item_unavailable",

            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_PROMO,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_A,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_B,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_C,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_D,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_E,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_F,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_G,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_H,
            BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_I
    );


    private static final long ONE_SECOND = 1000L;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;
    private static final long ONE_DAY = ONE_HOUR * 24;

    private static final long PERIOD_START_NAGGING = ONE_DAY * 180;
    private static final long PERIOD_FULL_NAGGING = ONE_DAY * 240;

    private static long FIRST_POSSIBLE_NAG_DAY = 1501372800000L;  //  07/30/17 0:00 AM

    private static final long NAG_MIN_DELAY = ONE_SECOND * 10;
    private static final long NAG_MAX_DELAY = ONE_MINUTE * 5;


    private static Long mInstallDate;

    private static IabUtil INSTANCE;
    private final Context mCtx;
    private final IabHelper mIabHelper;
    private final Handler mIabHandler;
    private final Handler mMainHandler;
    private IabResult mIabSetupResult;


    private Inventory mInventory;
    private List<FullVersionListener> mListeners = new ArrayList<>();

    public static boolean isGooglePlayInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo("com.android.vending", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public synchronized static IabUtil getInstance(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new IabUtil(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    public IabUtil(Context ctx) {
        mCtx = ctx;

        HandlerThread aHandlerThread = new HandlerThread(
                "IabHandler");
        aHandlerThread.start();
        mIabHandler = new Handler(aHandlerThread.getLooper());

        mMainHandler = new Handler(ctx.getMainLooper());

        mIabHelper = new IabHelper(ctx, BASE64PUBLICKEY);
        mIabHelper.enableDebugLogging(BuildConfig.DEBUG, "IAB-H");
        mIabHelper.startSetup(this);
    }

    public boolean isIabAvailable() {
        return isGooglePlayInstalled(mCtx) && !BuildConfig.IS_FRDOID && mIabSetupResult != null && mIabSetupResult.isSuccess();
    }

    public synchronized void addListener(FullVersionListener v) {
        mListeners.add(v);
    }

    public synchronized void removeListener(FullVersionListener v) {
        mListeners.remove(v);
    }

    private synchronized void fireChange(final boolean isFullVersion) {

        for (FullVersionListener v : mListeners) {
            invokeCallback(v, isFullVersion);
        }

    }


    public synchronized void doIfFullOrShowPurchaseDialog(final Activity activity, final Runnable okRunnable, final int requestCode) {
        checkFullVersion(new FullVersionListener() {
            @Override
            public void onFullVersion_MAIN_THREAD(boolean isFullVersion) {
                if (isFullVersion) {
                    okRunnable.run();
                } else {
                    makePurchaseDialog(activity)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    showPurchaseActivity(activity, requestCode);
                                }
                            })
                            .show();

                }
            }
        });


    }

    public synchronized void doIfFullOrShowPurchaseDialog(final Fragment fragment, final Runnable okRunnable, final int requestCode) {
        checkFullVersion(new FullVersionListener() {
            @Override
            public void onFullVersion_MAIN_THREAD(boolean isFullVersion) {
                if (isFullVersion) {
                    okRunnable.run();
                } else {
                    makePurchaseDialog(fragment.getActivity())
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    showPurchaseActivity(fragment, requestCode);
                                }
                            })
                            .show();

                }
            }
        });

    }

    private MaterialDialog.Builder makePurchaseDialog(Context ctx) {
        return new MaterialDialog.Builder(ctx)
                .title(R.string.upgrade_interstitial_title)
                .content(R.string.upgrade_interstitial_content)
                .positiveText(R.string.action_upgrade)
                .negativeText(R.string.action_cancel)
                .cancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        //NOOP
                    }
                });
    }

    public synchronized void checkFullVersionAndLoadSkuDetails(FullVersionListener callback) {
        checkFullVersion(true, callback);
    }

    public synchronized void checkFullVersion(FullVersionListener callback) {
        checkFullVersion(false, callback);
    }

    public synchronized void checkFullVersion(final boolean loadSkuDetails, final FullVersionListener callback) {
        if (!isIabAvailable() || BuildConfig.DEBUG) {
            Ln.d("IAB, queryInventory impossible no IAB support!");
            if (callback != null) {
                invokeCallback(callback, true);

            } else {
                fireChange(false);
            }
            return;
        }

        mIabHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mIabHelper.queryInventoryAsync(loadSkuDetails, SKUS_FULL_VERSION, new IabHelper.QueryInventoryFinishedListener() {
                        @Override
                        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                            if (result.isSuccess()) {
                                boolean fullVersion = false;
                                for (String sku : SKUS_FULL_VERSION) {
                                    Purchase p = inv.getPurchase(sku);
                                    // 0 (purchased), 1 (canceled), or 2 (refunded).
                                    if (p != null && p.getPurchaseState() == 0) {
                                        fullVersion = true;
                                        break;
                                    }
                                }
                                Ln.d("IAB, onQueryInventoryFinished, fullVersion=%s", fullVersion);
                                mInventory = inv;
                                if (callback != null) {
                                    invokeCallback(callback, fullVersion);
                                } else {
                                    fireChange(fullVersion);
                                }
                            } else {
                                Ln.d("IAB, queryInventory failed: %s ", result.getMessage());
                                if (callback != null) {
                                    invokeCallback(callback, false);
                                } else {
                                    fireChange(false);
                                }
                            }
                            processPendingAsyncRequests();
                        }
                    });
                } catch (IllegalStateException ex) {
                    Ln.e(ex, "exception in checkFullVersion");
                    if (ex.getMessage().contains("because another async operation") || ex.getMessage().contains("IAB helper is not set up")) {
                        addPendingAsyncRequest(new Runnable() {
                            @Override
                            public void run() {
                                checkFullVersion(loadSkuDetails, callback);
                            }
                        });
                    } else {
                        if (callback != null) {
                            invokeCallback(callback, false);
                        } else {
                            fireChange(false);
                        }
                    }
                }

            }
        });
    }

    private void invokeCallback(final FullVersionListener callback, final boolean isFullVersion) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFullVersion_MAIN_THREAD(isFullVersion);
            }
        });

    }


    private List<Runnable> q = Collections.synchronizedList(new ArrayList<Runnable>());


    private synchronized void addPendingAsyncRequest(final Runnable r) {
        mIabHandler.post(new Runnable() {
            @Override
            public void run() {
                q.add(r);
            }
        });
    }

    private synchronized void processPendingAsyncRequests() {
        mIabHandler.post(new Runnable() {
            @Override
            public void run() {
                List<Runnable> workQ = q;
                q = Collections.synchronizedList(new ArrayList<Runnable>());
                for (Runnable r : workQ) {
                    mIabHandler.post(r);
                }
            }
        });
    }

    public void onPurchaseFinished(IabResult result, Purchase info) {
        if (result.isSuccess()) {
            String sku = info.getSku();
            boolean fullVersion = Arrays.asList(SKUS_FULL_VERSION).contains(sku);
            Ln.d("IAB, onPurchaseFinished, fullVersion=%s", fullVersion);
            fireChange(fullVersion);
        }
    }

    public void showPurchaseActivity(Fragment f, int requestCode) {
        PurchaseActivity.showForResult(f, requestCode);
    }

    public void showPurchaseActivity(Context ctx) {
        PurchaseActivity.show(ctx);
    }

    public void showPurchaseActivity(Activity a, int requestCode) {
        PurchaseActivity.showForResult(a, requestCode);
    }

    private long getAge(Context ctx) {
        if (mInstallDate == null) {
            try {
                mInstallDate = ctx
                        .getPackageManager()
                        .getPackageInfo(ctx.getPackageName(), 0)
                        .firstInstallTime;
            } catch (PackageManager.NameNotFoundException e) {
                //can never happen
            }
        }
        long age = System.currentTimeMillis() - mInstallDate;
        return age;
    }


    public boolean isShowUpgradeButton(Context ctx) {
        if (!isIabAvailable()||BuildConfig.DEBUG) {
            return false;
        }

        if (mInventory == null) {
            checkFullVersion(null);
            return false; //don't show if inventory not yet loaded
        }
        boolean fullVersion = false;
        for (String sku : SKUS_FULL_VERSION) {
            if (mInventory.hasPurchase(sku)) {
                fullVersion = true;
                break;
            }
        }
        if (fullVersion) {
            return false;
        }
        return System.currentTimeMillis() >= FIRST_POSSIBLE_NAG_DAY
                &&
                getAge(ctx) >= (BuildConfig.DEBUG ? 0 : PERIOD_START_NAGGING);
    }

    public long getUpgradeButtonDelay(Context context) {
        if (isShowUpgradeButton(context)) {
            if (BuildConfig.DEBUG) {
                return 1000;
            }

            double f = (double) (getAge(context) - PERIOD_START_NAGGING) /
                    (PERIOD_FULL_NAGGING - PERIOD_START_NAGGING);

            f = Math.min(1f, Math.max(0, f));
            f = 1 - f;

            long d = (long) (f * NAG_MAX_DELAY);
            long res = Math.max(NAG_MIN_DELAY, Math.min(NAG_MAX_DELAY, d));
            return res;
        } else {
            return 0;
        }
    }

    @Override
    public synchronized void onIabSetupFinished(IabResult result) {
        mIabSetupResult = result;
        if (!result.isSuccess()) {
            Ln.d("IAB, IAB setup failed: %s", result.getMessage());
        } else {
            IntentFilter promoFilter =
                    new IntentFilter("com.android.vending.billing.PURCHASES_UPDATED");
            mCtx.registerReceiver(this, promoFilter);

            processPendingAsyncRequests();
        }
    }

//    private boolean isIabSetupOk() {
//        return mIabSetupResult != null && mIabSetupResult.isSuccess();
//    }

    public Inventory getInventory() {
        return mInventory;
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        mIabHelper.handleActivityResult(requestCode, resultCode, data);
        processPendingAsyncRequests();
    }

    public void launchPurchaseFlow(final PurchaseActivity act, final String sku, final String itemType, final int requestCode, final IabHelper.OnIabPurchaseFinishedListener listener) {
        if (!isIabAvailable()) {
            return;
        }
        try {
            mIabHelper.launchPurchaseFlow(act, sku, itemType, requestCode, listener, null);
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
            addPendingAsyncRequest(new Runnable() {
                @Override
                public void run() {
                    launchPurchaseFlow(act, sku, itemType, requestCode, listener);
                }
            });

        }
    }


    public String[] getActiveFullVersionSkusWithoutPromo() {
        if (mInventory == null) {
            return null;
        }
        List<String> r = new ArrayList<>();

        for (String sku : SKUS_FULL_VERSION) {
            if (!BuildConfig.GOOGLE_PLAY_SKU_FULLVERSION_PROMO.equals(sku)) {
                if (mInventory.hasDetails(sku)) {
                    r.add(sku);
                }
            }
        }


        String[] res = new String[r.size()];
        return r.toArray(res);
    }

    public void consumeAll(final Runnable runnable) {
        try {
            if (mInventory != null) {
                mIabHelper.consumeAsync(mInventory.getAllPurchases(), new IabHelper.OnConsumeMultiFinishedListener() {
                    @Override
                    public void onConsumeMultiFinished(List<Purchase> purchases, List<IabResult> results) {
                        Ln.d("IAB ALL CONSUMED!");
                        runnable.run();
                    }
                });
            }
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.android.vending.billing.PURCHASES_UPDATED".equals(intent.getAction())) {
            checkFullVersion(null);
        }
    }

}
