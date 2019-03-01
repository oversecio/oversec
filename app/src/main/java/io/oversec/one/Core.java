package io.oversec.one;


import android.app.Activity;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.*;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.*;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.protobuf.InvalidProtocolBufferException;

import io.oversec.one.acs.OversecAccessibilityService;
import io.oversec.one.acs.Tree;
import io.oversec.one.acs.util.AccessibilityNodeInfoUtils;
import io.oversec.one.common.Consts;
import io.oversec.one.common.CoreContract;
import io.oversec.one.common.MainPreferences;
import io.oversec.one.crypto.*;
import io.oversec.one.crypto.encoding.XCoderFactory;
import io.oversec.one.crypto.encoding.pad.PadderContent;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.gpg.OpenPGPErrorException;
import io.oversec.one.crypto.gpg.OpenPGPParamsException;
import io.oversec.one.crypto.proto.Inner;
import io.oversec.one.crypto.proto.Outer;
import io.oversec.one.crypto.sym.OversecKeystore2;
import io.oversec.one.crypto.symbase.KeyUtil;
import io.oversec.one.db.Db;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;
import io.oversec.one.db.PadderDb;
import io.oversec.one.iab.IabUtil;
import io.oversec.one.ovl.NodeTextView;
import io.oversec.one.ovl.OverlayDialogToast;
import io.oversec.one.ui.*;
import roboguice.util.Ln;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class Core extends CoreContract implements Handler.Callback {
    public static final boolean CHECK_THREADS = BuildConfig.DEBUG;

    public static final int NOTIFICATION_ID = Consts.INSTANCE.getNextNotificationId();
    public static final int NOTIFICATION_ID_ACCESSIBILITY_NOT_RUNNING = Consts.INSTANCE.getNextNotificationId();

    private static final int WHAT_SINGLE_TAP_BTENCRYPT = 1;
    private static final int WHAT_DOUBLE_TAP_BTENCRYPT = 2;
    private static final int WHAT_LONG_TAP_BTENCRYPT = 3;
    private static final int WHAT_SINGLE_TAP_BTDECRYPT = 4;
    private static final int WHAT_LONG_TAP_BTDECRYPT = 6;
    private static final int WHAT_SINGLE_TAP_BTINFOMODE = 7;
    private static final int WHAT_LONG_TAP_BTINFOMODE = 9;
    private static final int WHAT_SINGLE_TAP_BTHIDE = 10;
    private static final int WHAT_LONG_TAP_BTHIDE = 12;
    private static final int WHAT_SINGLE_TAP_BTCONFIG = 13;
    private static final int WHAT_LONG_TAP_BTCONFIG = 15;
    private static final int WHAT_PERFORM_DECRYPT = 16;
    private static final int WHAT_TOGGLE_HIDE = 17;
    private static final int WHAT_BOSS_KEY = 18;
    private static final int WHAT_SET_TEXT = 19;
    private static final int WHAT_DO_ENCRYPT = 20;
    //   private static final int WHAT_EXEC_DECRYPT_UI_REQ = 21;
    private static final int WHAT_TOGGLE_INFOMODE = 22;

    private static final int WHAT_SCRAPE_COMPLETED = 24;
    private static final int WHAT_CONFIGURATION_CHANGED = 25;
    private static final int WHAT_BRINGUP_SKB = 26;
    private static final int WHAT_REMOVE_OVL_DECRYPT = 27;
    private static final int WHAT_HIDE_TOAST = 28;
    private static final int WHAT_UNDO_HIDE = 29;
    private static final int WHAT_UPDATE_BUTTON_VISIBILITIES = 30;
    private static final int WHAT_DECRYPT_OVERLAY_PARAMS_CHANGED = 31;
    private static final int WHAT_PROCESS_REMAINING_PENDING_USERINTERACTIONS = 32;
    public static final int WHAT_SINGLE_TAP_BTCAMERA = 34;
    private static final int WHAT_DOUBLE_TAP_HIDE = 37;
    private static final int WHAT_SINGLE_TAP_BTUPGRADE = 38;
    private static final int WHAT_REMOVE_DIALOG_UPGRADE = 39;
    private static final int WHAT_CLEAR_IGNORED_TEXTS = 40;
    public static final int WHAT_SINGLE_TAP_BTCOMPOSE = 41;
    public static final int WHAT_ENCRYPT_CLIPBOARD = 42;
    private static final int WHAT_SHOW_IGNORE_TEXT_DIALOG = 43;
    private static final int WHAT_LAST_WINDOW_STATE_CHANGE_CLASSNAME = 44;

    public static final String PACKAGE_SYTEMUI = "com.android.systemui";

    public static final long DELAY_REPEAT_WHILE_NODE_NOT_FOUND = 200;
    private static final int MAX_REPEAT_WHILE_NODE_NOT_FOUND = 5;
    private static final int CLICK_NODE_WHEN_NODE_NOT_FOUND_REPEAT_COUNT = 2; //must be less than MAX_REPEAT_WHILE_NODE_NOT_FOUND
    private static final Object DUMMY = new Object();

    private final Context mCtx;

    private static Core INSTANCE;
    private final Db mDb;
    private final Overlays mOverlays;
    private final Handler mUiHandler;
    private final HandlerThread mHandlerThread;
    private final Thread mMainThread;
    private final Handler mMainHandler;

    private ClipboardManager mCbm = null;
    private OversecAccessibilityService mOversecAccessibilityService;

    private final NotificationManager mNm;
    private CryptoHandlerFacade mCryptoHandlerFacade;
    private EncryptionCache mEncryptionCache;


    private final UiThreadVars mUiThreadVars;

    private Stack<String> mPendingUserInteractions = new Stack<>();
    private Stack<String> mPendingUserInteractionsImmediate = new Stack<>();


    private static LinkedHashMap<String, Object> mIgnoredSimpleKeyTexts = new LinkedHashMap<String, Object>() {
        public static final int MAX_CACHE_ENTRIES = 500;


        @Override
        protected boolean removeEldestEntry(Entry<String, Object> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };
    private boolean mIgnoreTextDialogActivityIsShowing;
    private boolean mInitiallyDisabled;

    public int getNumIgnoredTexts() {
        return mIgnoredSimpleKeyTexts.size();
    }

    private boolean mUserInteractionDialogConfirmed = false;
    private boolean mScreenOn = true;
    private boolean mAcsCheckerIntervalFast = false;


    //TODO. refactor
    public CryptoHandlerFacade getEncryptionHandler() {
        return mCryptoHandlerFacade;
    }

    public void onImeStatusChanged(OversecAccessibilityService.IME_STATUS status) {
        Ln.d("IME: STATUSCHANGED: %s", status.name());
    }

    public boolean isImeFullscreen() {
        return mOversecAccessibilityService == null ? false : mOversecAccessibilityService.isImeFullscreen();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(WHAT_CONFIGURATION_CHANGED, newConfig));
    }

    public void onConfigurationChanged_UI(Configuration newConfig) {
        checkUiThread();

        final int orientation = newConfig.orientation;
        if (orientation == mUiThreadVars.getLastOrientation()) {
            return;
        }

        mUiThreadVars.setLastOrientation(orientation);
        mOverlays.onOrientationChanged(orientation);
        startPreemptiveRefresh_UI();
        if (mOversecAccessibilityService != null) {
            mOversecAccessibilityService.sendScrapeAllMessage("onConfigurationChanged_UI", null);
        }
    }

    public int getOrientation_UI() {
        return mUiThreadVars.getLastOrientation();
    }

    public synchronized static Core getInstance(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new Core(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    private Core(Context ctx) {
        mCtx = ctx;

        mMainThread = Looper.getMainLooper().getThread();

        mUiThreadVars = new UiThreadVars(this);

        mHandlerThread = new HandlerThread(
                "UI");
        mHandlerThread.setPriority(Thread.MAX_PRIORITY);
        mHandlerThread.start();
        mUiHandler = new Handler(mHandlerThread.getLooper(), this);

        mMainHandler = new Handler(Looper.getMainLooper(), mMainHandlerCallback);

        mDb = new Db(this);
        mCryptoHandlerFacade = CryptoHandlerFacade.Companion.getInstance(mCtx);
        mEncryptionCache = new EncryptionCache(mCtx, mCryptoHandlerFacade);
        // mIgnoreCache = IgnoreUIRQCache.getInstance(mCtx);
        mOverlays = new Overlays(this, ctx);
        mNm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        mCbm = (ClipboardManager) mCtx.getSystemService(Context.CLIPBOARD_SERVICE);


        setOversecAccessibilityService(mOversecAccessibilityService); //force notification update


        BroadcastReceiver aIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String action = intent.getAction();
                    Ln.d("OOPS %s", action);
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        mScreenOn = false;
                        onScreenOff_UI();
                    } else {
                        if (Intent.ACTION_SCREEN_ON.equals(action)) {
                            mScreenOn = true;
                            onScreenOn_UI();

                        }
                    }
                } catch (Throwable ex) {
                    Ln.e(ex, "WTF ?");
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mCtx.registerReceiver(aIntentReceiver, filter, null, mUiHandler);

        CoreContract.Companion.init(this);
    }

    ObservableCore mObservableCore = new ObservableCore();

    public Observable getObservableCore() {
        return mObservableCore;
    }

    public void setOversecAccessibilityService(OversecAccessibilityService oversecAccessibilityService) {

        if (oversecAccessibilityService != mOversecAccessibilityService) {
            mObservableCore.setChanged();
        }

        if (oversecAccessibilityService != null) {
            mNm.cancel(NOTIFICATION_ID_ACCESSIBILITY_NOT_RUNNING);
        } else {
            Notification aNotification = OversecIntentService.buildAcsNotRunningNotification(mCtx);
            mNm.notify(NOTIFICATION_ID_ACCESSIBILITY_NOT_RUNNING, aNotification);
        }

        mOversecAccessibilityService = oversecAccessibilityService;
        mObservableCore.notifyObservers();
    }

    public boolean isAccessibilityServiceRunning() {
        return mOversecAccessibilityService != null;
    }

    public Db getDb() {
        return mDb;
    }

    public void doTemporaryHide(String packageName, Boolean hide) {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(WHAT_TOGGLE_HIDE, new TemporaryHideParams(packageName, hide)));
    }

    public OversecAccessibilityService getAcs() {
        return mOversecAccessibilityService;
    }

    public Context getCtx() {
        return mCtx;
    }


    public void onButtonEncryptSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTENCRYPT, null));
    }

    public void onButtonEncryptLongTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_LONG_TAP_BTENCRYPT, mOversecAccessibilityService.isImeVisible()));
    }

    public void onButtonEncryptDoubleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_DOUBLE_TAP_BTENCRYPT, mOversecAccessibilityService.isImeVisible()));
    }

    public void onButtonDecryptSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTDECRYPT, null));
    }

    public void onButtonDecryptLongTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_LONG_TAP_BTDECRYPT));
    }

    public void onButtonInfomodeSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTINFOMODE, null));
    }

    public void onButtonInfomodeLongTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_LONG_TAP_BTINFOMODE));
    }

    public void onButtonHideSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTHIDE, null));
    }

    public void onButtonHideDoubleTap() {
        mUiHandler.sendEmptyMessage(WHAT_DOUBLE_TAP_HIDE);
    }

    public void onButtonHideLongTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_LONG_TAP_BTHIDE));
    }

    public void onButtonUpgradeSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTUPGRADE, null));
    }

    public void onButtonConfigSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTCONFIG, null));
    }

    public void onButtonConfigLongTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_LONG_TAP_BTCONFIG));
    }

    public void onButtonCameraSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTCAMERA, null));
    }

    public void onButtonComposeSingleTap() {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SINGLE_TAP_BTCOMPOSE, null));
    }

    public void doDecrypt(Intent actionData, boolean bringUpSkb) {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_PERFORM_DECRYPT,
                new DoDecryptParams(actionData, bringUpSkb, 0)));
    }

    public void postRunnableOnUiThread(Runnable runnable) {
        mUiHandler.post(runnable);
    }

    @Override
    public boolean handleMessage(Message msg) {
        checkUiThread();

        switch (msg.what) {
            case WHAT_SINGLE_TAP_BTUPGRADE:
                showUpgradeDialog_UI();
                return true;
            case WHAT_SINGLE_TAP_BTENCRYPT:
                performSingleTapBtEncrypt_UI((Intent) msg.obj, mOverlays.getEncryptButtonView());
                return true;
            case WHAT_SINGLE_TAP_BTDECRYPT:
                performSingleTapBtDecrypt_UI((Intent) msg.obj);
                return true;
            case WHAT_SINGLE_TAP_BTINFOMODE:
                performSingleTapBtInfomode_UI();
                return true;
            case WHAT_SINGLE_TAP_BTHIDE:
                doTemporaryHide_UI(new TemporaryHideParams(mUiThreadVars.getCurrentPackageName(), null));
                return true;
            case WHAT_SINGLE_TAP_BTCONFIG:
            case WHAT_DOUBLE_TAP_HIDE:
                mOverlays.removeDecrypt(); //optimization, don't need to wait for the ACS event
                AppConfigActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), mOverlays.getConfigButtonView());
                return true;
            case WHAT_SINGLE_TAP_BTCAMERA:
                fireCameraIntent_UI();
                return true;
            case WHAT_SINGLE_TAP_BTCOMPOSE:
                fireComposeIntent_UI();
                return true;
            case WHAT_LONG_TAP_BTENCRYPT:
                showEncryptionParamsActivity_UI((boolean) msg.obj, mOverlays.getEncryptButtonView());
                return true;
            case WHAT_DOUBLE_TAP_BTENCRYPT:
                onButtonEncryptDoubleTap_UI();
                return true;
            case WHAT_LONG_TAP_BTDECRYPT:
                //NOOP
                return true;
            case WHAT_LONG_TAP_BTINFOMODE:
                AppConfigActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), null);
            case WHAT_LONG_TAP_BTCONFIG:
                //NOOP
                return true;
            case WHAT_LONG_TAP_BTHIDE:
                panic_UI();
                return true;
            case WHAT_PERFORM_DECRYPT:
                doDecrypt_UI((DoDecryptParams) msg.obj);
                return true;
            case WHAT_TOGGLE_HIDE:
                doTemporaryHide_UI((TemporaryHideParams) msg.obj);
                return true;
            case WHAT_BOSS_KEY:
                panic_UI();
                return true;
            case WHAT_SET_TEXT:
                setText_UI((SetTextParams) msg.obj);
                return true;
            case WHAT_DO_ENCRYPT:
                doEncrypt_UI((DoEncryptParams) msg.obj);
                return true;
            case WHAT_TOGGLE_INFOMODE:
                toggleInfoMode_UI((Boolean) msg.obj, msg.arg1);
                return true;
            case WHAT_SCRAPE_COMPLETED:
                onAcsScrapeComplete_UI((ScrapeCompletedParams) msg.obj);
                return true;
            case WHAT_CONFIGURATION_CHANGED:
                onConfigurationChanged_UI((Configuration) msg.obj);
                return true;
            case WHAT_BRINGUP_SKB:
                bringUpSkb_UI();
                return true;
            case WHAT_REMOVE_OVL_DECRYPT:
                removeOverlayDecrypt_UI((Boolean) msg.obj);
                return true;
            case WHAT_HIDE_TOAST:
                mOverlays.removeDialogOverlayToast();
                return true;
            case WHAT_UNDO_HIDE:
                undoTemporaryHide_UI((String) msg.obj);
                return true;
            case WHAT_UPDATE_BUTTON_VISIBILITIES:
                updateButtonVisibilities_UI();
                return true;
            case WHAT_DECRYPT_OVERLAY_PARAMS_CHANGED:
                onDecryptOverlayLayoutParamsChanged_UI((String) msg.obj);
                return true;
            case WHAT_PROCESS_REMAINING_PENDING_USERINTERACTIONS:
                processPendingEncodedTextsRequiringUserInteraction_UI();
                return true;
            case WHAT_REMOVE_DIALOG_UPGRADE:
                removeUpgradeDialog_UI();
                return true;
            case WHAT_CLEAR_IGNORED_TEXTS:
                clearIgnoredTexts_UI();
                return true;
            case WHAT_ENCRYPT_CLIPBOARD:
                Object[] p = (Object[]) msg.obj;
                doEncryptAndPutToClipboard_UI((String) p[0], (String) p[1], (Intent) p[2], (byte[]) p[3]);
                return true;
            case WHAT_SHOW_IGNORE_TEXT_DIALOG:
                String s = (String) msg.obj;
                IgnoreTextDialogActivity.show(mCtx, s);
                return true;
            case WHAT_LAST_WINDOW_STATE_CHANGE_CLASSNAME: {
                Object[] ps = (Object[]) msg.obj;
                setLastWindowStateChangedClassName_UI((CharSequence) ps[0], (String) ps[1]);
                return true;
            }
        }
        return false;
    }

    private void onButtonEncryptDoubleTap_UI() {
        mOverlays.tempHideEncryptButton();
    }

    private void fireCameraIntent_UI() {
        TakePhotoActivity.show(mCtx, mUiThreadVars.getCurrentPackageName());
    }

    private void fireComposeIntent_UI() {
        String prefill = null;
        try {
            ClipData clipData = mCbm.getPrimaryClip();

            if (clipData != null) {
                ClipData.Item item = clipData.getItemAt(0);
                if (item != null) {
                    prefill = item.coerceToText(mCtx).toString();

                    //check if already encrypted

                    try {
                        BaseDecryptResult tdr = mCryptoHandlerFacade.decrypt(prefill, null);
                        if (tdr != null && tdr.isOk()) {
                            //current editor content is ENCRYPTED,- > NOT doing prefill
                            prefill = null;
                        } else {
                            //current editor content is NOT encrypted
                        }
                    } catch (UserInteractionRequiredException e) {
                        prefill = null;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        ComposeActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), prefill);
    }

    private void bringUpSkb_UI() {
        checkUiThread();
        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {

                @Override
                public void performAction(final AccessibilityNodeInfo node) {
                    bringUpSkb(node);
                }

                @Override
                public void performActionWhenNothingFocused() {
                    Ln.w("No focused node found!");
                }
            });
        }
    }

    private void doTemporaryHide_UI(TemporaryHideParams p) {
        checkUiThread();

        Boolean hide = p.hide;
        if (hide == null) {
            hide = !isTemporaryHidden_UI(p.packageName);
        }

        mUiThreadVars.setTemporaryHidden(p.packageName, hide);
        mOverlays.setHidden(hide);
        showOrUpdateNotification_UI();

        if (!hide) {
            if (mOversecAccessibilityService!=null) {
                mOversecAccessibilityService.sendScrapeAllMessage("doTemporaryHide_UI", null);
            }
        } else {
            mOverlays.refreshDecrypt(null);
            mEncryptionCache.clear(EncryptionCache.CLEAR_REASON.OVERSEC_HIDDEN, null);
        }
    }

    //TODO: BAD, had to weaken access to those variable for a regular activity (which runs in the real UI == main thread)
    public boolean isTemporaryHidden(String packageName) {
        if (packageName == null) {
            Ln.w("got null packageName");
            return false;
        }

        Boolean r = mUiThreadVars.isTemporaryHidden(packageName);
        if (r == null) {
            r = false;
        }
        return r;
    }

    public boolean isTemporaryHidden_UI(String packageName) {
        checkUiThread();

        if (packageName == null) {
            Ln.w("got null packageName");
            return false;
        }

        Boolean r = mUiThreadVars.isTemporaryHidden(packageName);
        if (r == null) {
            r = mDb.isStartHidden(packageName);
            mUiThreadVars.setTemporaryHidden(packageName, r);
        }
        return r;
    }

    public void undoTemporaryHide(String packageName) {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(WHAT_UNDO_HIDE, packageName));
    }

    public void undoTemporaryHide_UI(String packageName) {
        checkUiThread();
        mUiThreadVars.setTemporaryHidden(packageName, false);
    }

    private void showEncryptionParamsActivity_UI(final boolean imeWasVisible, final View view) {
        checkUiThread();
        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {

                @Override
                public void performAction(final AccessibilityNodeInfo node) {
                    removeOverlayDecrypt_UI(false);
                    EncryptionParamsActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), getNodeText(node), node.hashCode(), imeWasVisible, view);
                }

                @Override
                public void performActionWhenNothingFocused() {
                    Ln.w("No focused node found!");
                }
            });
        }
    }

    private void performSingleTapBtInfomode_UI() {
        checkUiThread();
        if (mOverlays.isInfoMode()) {
            toggleInfoMode_UI(false, 0);
            return;
        }
        toggleInfoMode_UI(true, 0);
    }

    private void performSingleTapBtEncrypt_UI(final Intent data, final View view) {
        checkUiThread();
        if (mOversecAccessibilityService == null) {
            return;
        }
        final boolean aSkbWasUp = mOversecAccessibilityService.isImeVisible();

        mOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {

            @Override
            public void performAction(final AccessibilityNodeInfo node) {
                checkUiThread();

                final String targetText = getNodeText(node).toString();

                try {
                    Ln.d(" doEncrypt_UI...performSingleTapBtEncrypt_UI...  ....ok");
                    BaseDecryptResult tdr = mCryptoHandlerFacade.decrypt(targetText, data);
                    if (tdr != null && tdr.isOk()) {
                        //current editor content is ENCRYPTED,- > NOT reencrypting an already encrypted text!
                    } else {
                        //current editor content is NOT encrypted

                        AbstractEncryptionParams ep =
                                mUiThreadVars.getUserSelectedEncryptionParamsX(mUiThreadVars.getCurrentPackageName());
                        if (ep == null
                                || !ep.isStillValid(mCtx)
                                || mDb.isForceEncryptionParams(mUiThreadVars.getCurrentPackageName())) {
                            mOverlays.removeDecrypt(); //optimization, don't need to wait for the ACS event
                            EncryptionParamsActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), getNodeText(node), node.hashCode(), aSkbWasUp, view);
                        } else {
                            doEncrypt_UI(ep, node.hashCode(), aSkbWasUp, 0, false, false, null, mUiThreadVars.getCurrentPackageName());
                        }
                    }
                } catch (UserInteractionRequiredException e) {
                    Ln.d(" doEncrypt_UI...performSingleTapBtEncrypt_UI...  ....UserInteractionRequiredException");
                    mOverlays.removeDecrypt(); //optimization, don't need to wait for the ACS event
                    EncryptionParamsActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), getNodeText(node), node.hashCode(), aSkbWasUp, view);
                }


            }

            @Override
            public void performActionWhenNothingFocused() {
                Ln.w("No focused node found!");
            }
        });
    }

    private void performSingleTapBtDecrypt_UI(final Intent data) {
        checkUiThread();

        final boolean aSkbWasUp = mOversecAccessibilityService.isImeVisible();

        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {


                @Override
                public void performAction(final AccessibilityNodeInfo node) {
                    checkUiThread();
                    final String targetText = getNodeText(node).toString();


                    try {
                        Ln.d(" doEncrypt_UI...performSingleTapBtDecrypt_UI...  ....ok");
                        BaseDecryptResult tdr = mCryptoHandlerFacade.decrypt(targetText, data);
                        if (tdr != null && tdr.isOk()) {
                            String text = "";
                            //on pre Lollipop we can set text only through clipboard.
                            //putting hte decryted text into the clipboard is potentially unsafe,
                            //so on those devices we simply clear out the text
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                try {
                                    Inner.InnerData innerData = tdr.getDecryptedDataAsInnerData();
                                    if (innerData.hasTextAndPaddingV0()) {
                                        text = innerData.getTextAndPaddingV0().getText();
                                    }
                                } catch (InvalidProtocolBufferException e) {
                                    try {
                                        text = tdr.getDecryptedDataAsUtf8String();
                                    } catch (UnsupportedEncodingException e1) {
                                        e1.printStackTrace();
                                    }
                                }

                            }
                            setText_UI(node.hashCode(), text, aSkbWasUp, 0);
                        } else {
                            if (targetText.length() > 0) {
                                //backspace
                                setText_UI(node.hashCode(), "", aSkbWasUp, 0);
                            }
                        }
                    } catch (UserInteractionRequiredException e) {
                        Ln.d(" doEncrypt_UI...performSingleTapBtDecrypt_UI...  ....UserInteractionRequiredException");
                        //EncryptionParamsActivity.show(mCtx, mUiThreadVars.getCurrentPackageName(), node.getText(), node.hashCode(), aSkbWasUp);
                        setText_UI(node.hashCode(), "", aSkbWasUp, 0);
                    }
                }

                @Override
                public void performActionWhenNothingFocused() {
                    Ln.w("No focused node found!");
                }
            });
        }
    }

    public void bringUpSkb(int delay) {
        mUiHandler.sendEmptyMessageDelayed(WHAT_BRINGUP_SKB, delay);
    }

    public EncryptionCache getEncryptionCache() {
        return mEncryptionCache;
    }

    public void openHelpUrl(Help.ANCHOR anchor) {
        //TODO
        String url = "http://www.todo.com";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mCtx.startActivity(i);
    }


    public void showOverlayDialogInsufficientPadding_UI() {
        checkUiThread();
        mOverlays.showDialogInsufficientPadding();
    }

    public void dismissOverlayDialogInsufficientPadding_UI() {
        checkUiThread();
        mOverlays.removeDialogOverlayInsufficientPadding();
    }


    public void showOverlayDialogCorruptedEncoding_UI() {
        checkUiThread();
        mOverlays.showDialogCorruptedEncoding();
    }

    public void dismissOverlayDialogCorruptedEncoding_UI() {
        checkUiThread();
        mOverlays.removeDialogCorruptedEncoding();
    }


    public void dismissOverlayToast() {
        mOverlays.removeDialogOverlayToast();
    }

    public void dismissOverlayTooltip() {
        mOverlays.removeDialogOverlayTooltip();
    }


    public void showTooltip_UI(View anchor, String msg, String gotItId, Help.ANCHOR helpAnchor, boolean showEvenIfDecryptOverlayIsHidden) {
        mOverlays.showTooltip(anchor, msg, gotItId, helpAnchor, showEvenIfDecryptOverlayIsHidden);
    }

    public void dismissOverlayDialogIRQ() {
        checkUiThread();
        mOverlays.removeDialogOverlayUserInteractionRequired();

    }

    public void disablePanicMode() {
        getDb().disablePanicMode();
        Util.enableLauncherIcon(mCtx, true);
        if (mOversecAccessibilityService != null) {
            mOversecAccessibilityService.setMonitorEventTypesAll();
        }
    }

    public void updateButtonVisibilities() {
        mUiHandler.sendEmptyMessage(WHAT_UPDATE_BUTTON_VISIBILITIES);
    }

    private void updateButtonVisibilities_UI() {
        checkUiThread();
        mOverlays.updateButtonVisibilities();
    }

    private final Handler.Callback mMainHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_DECRYPT_OVERLAY_PARAMS_CHANGED:
                    onDecryptOverlayLayoutParamsChanged_MAIN((String) msg.obj);
                    return true;
            }
            return false;
        }
    };

    private final List<IDecryptOverlayLayoutParamsChangedListener> mDecryptOverlayLayoutParamsChangedListeners_MAIN = new ArrayList<>();

    public void addDecryptOverlayLayoutParamsChangedListenerMainThread(
            IDecryptOverlayLayoutParamsChangedListener listener) {
        mDecryptOverlayLayoutParamsChangedListeners_MAIN.add(listener);
    }

    public void removeDecryptOverlayLayoutParamsChangedListenerMainThread(
            IDecryptOverlayLayoutParamsChangedListener listener) {
        mDecryptOverlayLayoutParamsChangedListeners_MAIN.remove(listener);
    }

    private void onDecryptOverlayLayoutParamsChanged_MAIN(String packagename) {
        checkMainThread();
        //mOverlays.onDecryptOverlayLayoutParamsChanged(packagename);
        for (IDecryptOverlayLayoutParamsChangedListener listener : mDecryptOverlayLayoutParamsChangedListeners_MAIN) {
            listener.onDecryptOverlayLayoutParamsChanged(packagename);
        }
    }

    private final List<IDecryptOverlayLayoutParamsChangedListener> mDecryptOverlayLayoutParamsChangedListeners_UI = new ArrayList<>();

    public void addDecryptOverlayLayoutParamsChangedListenerUiThread(
            IDecryptOverlayLayoutParamsChangedListener listener) {
        mDecryptOverlayLayoutParamsChangedListeners_UI.add(listener);
    }

    public void removeDecryptOverlayLayoutParamsChangedListenerUiThread(
            IDecryptOverlayLayoutParamsChangedListener listener) {
        mDecryptOverlayLayoutParamsChangedListeners_UI.remove(listener);
    }

    private void onDecryptOverlayLayoutParamsChanged_UI(String packagename) {
        checkUiThread();
        //mOverlays.onDecryptOverlayLayoutParamsChanged(packagename);
        for (IDecryptOverlayLayoutParamsChangedListener listener : mDecryptOverlayLayoutParamsChangedListeners_UI) {
            listener.onDecryptOverlayLayoutParamsChanged(packagename);
        }
    }

    public void fireDecryptOverlayLayoutParamsChanged(String packagename) {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(WHAT_DECRYPT_OVERLAY_PARAMS_CHANGED, packagename));
        mMainHandler.sendMessage(mMainHandler.obtainMessage(WHAT_DECRYPT_OVERLAY_PARAMS_CHANGED, packagename));
    }

    public String getCurrentPackageName_UI() {
        checkUiThread();
        return mUiThreadVars.getCurrentPackageName();
    }

    public String getCurrentPackageName() {
        Ln.d("SERVICE: getCurrentPackageName=%s", mUiThreadVars.getCurrentPackageName());
        return mUiThreadVars.getCurrentPackageName();
    }

    public void onNodeTextViewDecrypted_UI(NodeTextView nodeTextView, BaseDecryptResult tdr, boolean uIRE) {
        checkUiThread();
        if (nodeTextView.isEditableEditText() && nodeTextView.isFocusedNode()) {
            mOverlays.onFocusedEditTextDecrypted(nodeTextView, tdr, uIRE);
        }
    }

    public void clearCurrentFocusedEditText_UI() {
        checkUiThread();
        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {
                @Override
                public void performAction(AccessibilityNodeInfo node) {
                    setText_UI(node.hashCode(), "", mOversecAccessibilityService.isImeVisible(), 1);
                }

                @Override
                public void performActionWhenNothingFocused() {

                }
            });
        }
    }

    public void onIgnoreTextDialogActivityClosed() {
        mIgnoreTextDialogActivityIsShowing = false;
    }

    public void setInitiallyDisabled(boolean disabled) {
        Ln.d("set initially disabled " + disabled);
        mInitiallyDisabled = disabled;
    }

    private class DoDecryptParams {
        private final Intent actionData;
        private final boolean bringUpSkb;
        private final int retryCount;

        public DoDecryptParams(Intent actionData, boolean bringUpSkb, int retryCount) {
            this.actionData = actionData;
            this.bringUpSkb = bringUpSkb;
            this.retryCount = retryCount;
        }
    }

    private void doDecrypt_UI(DoDecryptParams p) {
        checkUiThread();
        doDecrypt_UI(p.actionData, p.bringUpSkb, p.retryCount);
    }

    private void doDecrypt_UI(Intent data, final boolean bringUpSkb, final int retryCount) {
        checkUiThread();

        doDecryptCurrentFocusedNode_UI(data, bringUpSkb, 0);
    }

    public void onAcsScrapeCompleted(String packageName, Tree.TreeNode rootNode) {
        if (mInitiallyDisabled) {
            Ln.d("Ignoring, initially disabled!");
            return;
        }

        mUiHandler.sendMessage(mUiHandler.obtainMessage(WHAT_SCRAPE_COMPLETED, new ScrapeCompletedParams(packageName, rootNode)));
    }

    public void onAcsScrapeComplete_UI(ScrapeCompletedParams p) {
        checkUiThread();
        Ln.d("SKRAPE: onAcsScrapeCompleted  %s  :    %s", p.packageName, p.displayRoot);


        int lastDisplayTreeRootNodeId = p.displayRoot == null ? 0 : p.displayRoot.getKey();
        Integer aLastDisplayTreeRootNodeId =
                mUiThreadVars.getLastDisplayTreeRootNodeId(p.packageName);
        if (aLastDisplayTreeRootNodeId == null) {
            aLastDisplayTreeRootNodeId = 0;
        }

        if (aLastDisplayTreeRootNodeId != lastDisplayTreeRootNodeId) {
            {
                //basically we've switched activities or fragments in the app
                //TODO: verify that this works for fragments, maybe we need to check the id of the "ListView",
                //or maybe the whole ID chain from an encrypted node

                //NOTE: this also creates problem sif underlying activity got destroyed and recreated!

                Ln.d("SKRAPE: new root %s doesn't    match mLastDisplayTreeRootNodeId=%s", lastDisplayTreeRootNodeId, aLastDisplayTreeRootNodeId);

                //  mOverlays.removeDialogOverlayUserInteractionRequired();
                mOverlays.removeDialogOverlayInsufficientPadding();
                mOverlays.removeDialogCorruptedEncoding();
                mOverlays.removeDialogOverlayToast();
                mOverlays.removeDialogOverlayTooltip();
                mOverlays.removeDialogOverlayUserInteractionRequired();

                mUiThreadVars.setLastDIsplayTreeRootNodeId(p.packageName, lastDisplayTreeRootNodeId);

                resetUserInteractionStuff_UI("root node id changed", false);
            }
        }

        mUiThreadVars.setDisplayRoot(p.displayRoot);

        if (!p.packageName.equals(mUiThreadVars.getCurrentPackageName())) {
            Ln.d("SKRAPE: onAcsScrapeCompleted Package change detected %s    <<<<   %s", p.packageName, mUiThreadVars.getCurrentPackageName());

            String prevPackageName = mUiThreadVars.getCurrentPackageName();
            mUiThreadVars.setCurrentPackageName(p.packageName);


            if (mDb.isStartHidden(p.packageName)) {
                mUiThreadVars.setTemporaryHidden(p.packageName, true);
            }

            if (
                    PACKAGE_SYTEMUI.equals(p.packageName) || PACKAGE_SYTEMUI.equals(prevPackageName) ||
                            OpenKeychainConnector.PACKAGE_NAME.equals(p.packageName) || OpenKeychainConnector.PACKAGE_NAME.equals(prevPackageName) ||
                            mCtx.getPackageName().equals(p.packageName) || mCtx.getPackageName().equals(prevPackageName)
                    ) {
                //seems we jsut went to some user interaction dialog and back
            } else {
                resetUserInteractionStuff_UI("package change  prevPackageName=" + prevPackageName + "   p.PackageName: " + p.packageName, false);
            }

            if (p.displayRoot != null && mDb.isShowDecryptOverlay(mUiThreadVars.getCurrentPackageName())) {

                showOrUpdateNotification_UI();
                addOverlayDecrypt_UI(mUiThreadVars.getCurrentPackageName());

            } else {
                if (!PACKAGE_SYTEMUI.equals(mUiThreadVars.getCurrentPackageName())) {
                    removeNotification();
                }
                removeOverlayDecrypt_UI(false);
            }
        }

        if (mScreenOn) {
            NodeTextView focusedTextView = mOverlays.refreshDecrypt(p.displayRoot);
            String curPackageName = mUiThreadVars.getCurrentPackageName();
            mOverlays.onScrapeComplete(focusedTextView, curPackageName, mUiThreadVars.getLastWindowStateChangedClassName(curPackageName));
        }
    }

    private void addOverlayDecrypt_UI(final CharSequence newPackageName) {
        checkUiThread();

        if (mDb.isBossMode()) {
            return;
        }
        mOverlays.showDecrypt(newPackageName.toString());
        showOrUpdateNotification_UI();
    }

    private void removeOverlayDecrypt_UI(boolean resetCurrentPackageName) {
        checkUiThread();

        mOverlays.removeDecrypt();

        if (resetCurrentPackageName) {
            mUiThreadVars.setCurrentPackageName(PACKAGE_SYTEMUI);
        }

        showOrUpdateNotification_UI();
    }

    public void removeOverlayDecrypt(boolean resetCurrentPackageName) {
        mUiHandler.sendMessage(mUiHandler.obtainMessage(WHAT_REMOVE_OVL_DECRYPT, resetCurrentPackageName));
    }

    public void panic() {
        mUiHandler.sendEmptyMessage(WHAT_BOSS_KEY);
    }

    private void panic_UI() {

        OpenKeychainConnector.Companion.getInstance(mCtx).doPanicExit();
        OversecKeystore2.Companion.getInstance(mCtx).clearAllCaches();

        mDb.panic();
        mEncryptionCache.clear(EncryptionCache.CLEAR_REASON.PANIC, null);
        if (mOversecAccessibilityService != null) {
            mOversecAccessibilityService.setMonitorEventTypesNone();
        }
        removeOverlayDecrypt_UI(true);

        closeNotificationDrawer();
        removeNotification();

        if (MainPreferences.INSTANCE.isHideLauncherOnPanic(mCtx) && MainPreferences.INSTANCE.isDialerSecretCodeBroadcastConfirmedWorking(mCtx)) {
            Util.enableLauncherIcon(mCtx, false);
        }
        System.exit(0);
    }

    private class ScrapeCompletedParams {
        String packageName;
        Tree.TreeNode displayRoot;

        ScrapeCompletedParams(String newPackageName, Tree.TreeNode displayRoot) {
            this.packageName = newPackageName;
            this.displayRoot = displayRoot;
        }

    }

    void closeNotificationDrawer() {
        Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mCtx.sendBroadcast(it);
    }

    private boolean mLastNotificationIsInfoMode = false;
    private boolean mLastNotificationDecryptOverlayVisible = false;

    public void showOrUpdateNotification_UI() {
        checkUiThread();

        if (mDb.isBossMode()) {
            removeNotification();
            return;
        }

        boolean show = mDb.isShowNotification(mUiThreadVars.getCurrentPackageName());


        if (show) {

            if (PACKAGE_SYTEMUI.equals(mUiThreadVars.getCurrentPackageName())) {
                return;
            }

            boolean infoMode = isInfoMode();
            boolean decryptOverlayVisible = mOverlays.isDecryptOverlayVisible();


            if (mLastNotificationDecryptOverlayVisible != decryptOverlayVisible || mLastNotificationIsInfoMode != isInfoMode()) {
                Boolean hidden = mUiThreadVars.isTemporaryHidden(mUiThreadVars.getCurrentPackageName());
                if (hidden == null) {
                    hidden = false;
                }
                Notification aNotification = OversecIntentService.buildNotification(mCtx, mUiThreadVars.getCurrentPackageName(), decryptOverlayVisible, infoMode, hidden);
                mNm.notify(NOTIFICATION_ID, aNotification);
            } else {
                //Ln.d("NOTIFICATION: SKIPPING notification update");
            }

            mLastNotificationIsInfoMode = infoMode;
            mLastNotificationDecryptOverlayVisible = decryptOverlayVisible;
        } else {
            removeNotification();
        }
    }

    public void removeNotification() {
        mNm.cancel(NOTIFICATION_ID);

    }

    private void doDecryptCurrentFocusedNode_UI(final Intent actionIntent,
                                                final boolean bringUpSkb,
                                                final int retryCount) {
        checkUiThread();
        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {


                @Override
                public void performAction(final AccessibilityNodeInfo node) {
                    final String targetText = getNodeText(node).toString();

                    postRunnableOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Ln.d(" doDecryptCurrentFocusedNode_UI...  run");

                                BaseDecryptResult tdr = mCryptoHandlerFacade.decrypt(targetText, actionIntent);

                                if (tdr.isOk()) {
                                    Ln.d(" doDecryptCurrentFocusedNode_UI...  run....ok");
                                    //on pre Lollipop we can set text only through clipboard.
                                    //putting hte decryted text into the clipboard is potentially unsafe,
                                    //so on those devices we simply clear out the text
                                    String text = "";
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                        try {
                                            Inner.InnerData innerData = tdr.getDecryptedDataAsInnerData();
                                            if (innerData.hasTextAndPaddingV0()) {
                                                text = innerData.getTextAndPaddingV0().getText();
                                            }
                                        } catch (InvalidProtocolBufferException e) {
                                            try {
                                                text = tdr.getDecryptedDataAsUtf8String();
                                            } catch (UnsupportedEncodingException e1) {
                                                e1.printStackTrace();
                                            }
                                        }

                                    }
                                    // current editor content seems to be encrypted
                                    setText_UI(node.hashCode(), text, bringUpSkb, 0);

                                }

                            } catch (UserInteractionRequiredException e) {
                                Ln.d(" doDecryptCurrentFocusedNode_UI...  run....UserInteractionRequiredException");
                                if (PendingIntentFiringBlankActivity.isShowing()) {
                                    Ln.w("About to show UIRQ, but was already active!");
                                    return;
                                }

                                removeOverlayDecrypt_UI(false);

                                PendingIntentFiringBlankActivity.fire(mCtx, e.getPendingIntent(), 0, new PendingIntentFiringBlankActivity.PendingIntentResultCallback() {

                                    @Override
                                    public void onResultFromPendingIntentActivity(int requestCode, int resultCode, Intent data) {
                                        if (resultCode == Activity.RESULT_OK) {
                                            doDecrypt(data, bringUpSkb);
                                        } else {
                                            Ln.w("user cancelled pendingintent activity");
                                        }
                                    }

                                });
                            }
                        }
                    });


                }

                @Override
                public void performActionWhenNothingFocused() {
                    Ln.w("No focused node found!");
                    if (retryCount > MAX_REPEAT_WHILE_NODE_NOT_FOUND) {
                        Ln.w("NOT repeating!");
                        return;
                    }

                    //maybe a timing issues, repeat after a short delay
                    mUiHandler.sendMessageDelayed(
                            Message.obtain(mUiHandler, WHAT_PERFORM_DECRYPT,
                                    new DoDecryptParams(actionIntent, bringUpSkb, retryCount + 1)),
                            DELAY_REPEAT_WHILE_NODE_NOT_FOUND);
                }
            });
        }
    }

    public void doEncryptAndSaveParams(AbstractEncryptionParams encryptionParams, int nodeId, boolean skbWasVisible, boolean addLink, String packagename) {
        doEncrypt(encryptionParams, nodeId, null, skbWasVisible, 0, true, addLink, null, packagename);
    }

    public void doEncrypt(AbstractEncryptionParams params, final int nodeId, final Intent actionIntent, boolean skbWasVisible, int retryCount, boolean saveParams, boolean addLink, byte[] innerPadding, String packagename) {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_DO_ENCRYPT, new DoEncryptParams(
                params, nodeId, actionIntent, skbWasVisible, retryCount, saveParams, addLink, innerPadding, packagename)));
    }

    private void doEncrypt_UI(AbstractEncryptionParams params, final int nodeId, boolean skbWasVisible, int repeatCount, boolean saveParams, boolean addLink, byte[] innerPadding, String packagename) {
        checkUiThread();

        doEncrypt_UI(params, nodeId, null, skbWasVisible, repeatCount, saveParams, addLink, innerPadding, packagename);
    }

    private class DoEncryptParams {
        private final AbstractEncryptionParams params;
        private final boolean saveParams;
        private final byte[] innerPadding;
        public int nodeId;
        public Intent actionIntent;
        public boolean skbwAsVisible;
        public int retryCount;
        public boolean addLink;
        public String packagename;

        public DoEncryptParams(AbstractEncryptionParams params,
                               int nodeId,
                               Intent actionIntent,
                               boolean skbWasVisible,
                               int retryCount,
                               boolean saveParams,
                               boolean addLink,
                               byte[] innerPadding,
                               String packagename) {
            this.params = params;
            this.nodeId = nodeId;
            this.actionIntent = actionIntent;
            this.skbwAsVisible = skbWasVisible;
            this.retryCount = retryCount;
            this.saveParams = saveParams;
            this.addLink = addLink;
            this.innerPadding = innerPadding;
            this.packagename = packagename;
        }


    }

    private void doEncrypt_UI(DoEncryptParams p) {
        doEncrypt_UI(p.params, p.nodeId, p.actionIntent, p.skbwAsVisible, p.retryCount, p.saveParams, p.addLink, p.innerPadding, p.packagename);
    }

    private void doEncrypt_UI(final AbstractEncryptionParams params,
                              final int nodeId,
                              final Intent actionIntent,
                              final boolean skbWasVisible,
                              final int retryCount,
                              final boolean saveParams,
                              final boolean addLink,
                              final byte[] padding,
                              final String packagename) {
        checkUiThread();

        Ln.d("OOO: doEncryptUI nodeId=%s retryCount=%s ", nodeId, retryCount);
        byte[] aPadding = null;
        if (padding == null) {

            int maxInnerPadding = mDb.getMaxInnerPadding(packagename);
            if (maxInnerPadding > 0) {
                int p = (int) (Math.random() * maxInnerPadding);
                aPadding = KeyUtil.INSTANCE.getRandomBytes(p);
            }
        } else {
            aPadding = padding;
        }

        final byte[] innerPadding = aPadding;
        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(new OversecAccessibilityService.PerformFocusedNodeAction() {
                @Override
                public void performAction(AccessibilityNodeInfo node) {
                    int realNodeId = nodeId;

                    Ln.d("OOO: performAction");

                    //AAARGH, this will fail if the underlying activity has been destroyed in the meantime
                    //i.e. when developer options  "don't keep activities" is on!
                    //TODO: do we really need this check???
                    if (nodeId != node.hashCode()) {
                        Ln.w("OOO: Focused node %s is not the one we expected!", node.hashCode());

                        if (retryCount == CLICK_NODE_WHEN_NODE_NOT_FOUND_REPEAT_COUNT) {
                            //try to click it
                            Ln.w("OOO: CLICKING IT");
                            clickNodeById(nodeId);


                        }

                        if (retryCount < MAX_REPEAT_WHILE_NODE_NOT_FOUND) {

                            mOversecAccessibilityService.performNodeAction(new OversecAccessibilityService.PerformNodeAction() {

                                boolean found = false;

                                @Override
                                public void onNodeScanned(AccessibilityNodeInfo node) {

                                    if (node.isFocused() && node.hashCode() == nodeId) {
                                        found = true;
                                        doEncrypt(params, nodeId, actionIntent, skbWasVisible, retryCount + 1, saveParams, addLink, padding, packagename);
                                    }
                                }

                                @Override
                                public void onScrapeComplete() {
                                    if (!found) {
                                        mUiHandler.sendMessageDelayed(
                                                Message.obtain(mUiHandler, WHAT_DO_ENCRYPT, new DoEncryptParams(
                                                        params, nodeId, actionIntent, skbWasVisible, retryCount + 1, saveParams, addLink, padding, packagename)), DELAY_REPEAT_WHILE_NODE_NOT_FOUND);

                                    }
                                }
                            });

                            return;
                        } else {
                            Ln.w("OOO: GIVING UP!");
                            //node not found, so be it, use the current focused noode
                            realNodeId = node.hashCode();
                            //continue

                        }
                    }

                    final String packagename = node.getPackageName().toString();

                    String srcText = getNodeText(node).toString();

                    try {
                        String targetText = mCryptoHandlerFacade.encrypt(
                                params,
                                srcText,
                                mDb.isAppendNewLines(packagename),
                                innerPadding,
                                packagename,
                                actionIntent);

                        Integer limit = Issues.INSTANCE.getInputFieldLimit(packagename);
                        if (limit != null && targetText.length() > limit) {
                            showTooltip_UI(null,
                                    mCtx.getString(R.string.tooltip_encodingtoolong),
                                    mCtx.getString(R.string.tooltip_settextfailed),
                                    Help.ANCHOR.settextfailed, true);
                            return;
                        }


                        if (addLink) {
                            String linkText = "\n" + mCtx.getString(R.string.share_as_suffix);
                            if (limit == null || (targetText.length() + linkText.length()) <= limit) {
                                targetText += linkText;
                            }
                        }

                        //now check that we can actually decrypt, otherwise bring up pending uirq dialog immediately
                        try {
                            mCryptoHandlerFacade.decrypt(targetText, null);
                            Ln.d(" doEncrypt_UI...checkdecrypt...  ....ok");
                        } catch (UserInteractionRequiredException ex) {
                            Ln.d(" doEncrypt_UI...checkdecrypt...  ....UserInteractionRequiredException");
                            mPendingUserInteractionsImmediate.add(targetText);
                        }

                        new SetTextAction(realNodeId, targetText, skbWasVisible, 0, params).performAction(node);
                    } catch (final UserInteractionRequiredException e) {
                        e.printStackTrace();

                        if (PendingIntentFiringBlankActivity.isShowing()) {
                            Ln.w("about to show UIRQ, but was already active!");
                            return;
                        }

                        removeOverlayDecrypt_UI(false);

                        PendingIntentFiringBlankActivity.fire(mCtx, e.getPendingIntent(), 0, new PendingIntentFiringBlankActivity.PendingIntentResultCallback() {

                            @Override
                            public void onResultFromPendingIntentActivity(int requestCode, int resultCode, Intent data) {
                                if (resultCode == Activity.RESULT_OK) {

                                    doEncrypt(params, nodeId, data, skbWasVisible, 0, saveParams, addLink, innerPadding, packagename);
                                } else {
                                    Ln.w("user cancelled pendingintent activity");
                                }
                            }


                        });
                    } catch (OpenPGPParamsException epp) {
                        epp.printStackTrace();
                        //For some reason encryption could not even be started, so we need to show the encryption params activity (again)
                        showEncryptionParamsActivity_UI(skbWasVisible, null);
                    } catch (OpenPGPErrorException ep) {
                        //TODO: maybe catch OpenPgpErrorException elsewhere as well??

//                    switch (ep.getError().getErrorId()) {
//                        case OpenPgpError.CLIENT_SIDE_ERROR:
//                        case OpenPgpError.GENERIC_ERROR:
//                        case OpenPgpError.INCOMPATIBLE_API_VERSIONS:
//                        case OpenPgpError.NO_OR_WRONG_PASSPHRASE:
//                        case OpenPgpError.NO_USER_IDS:
                        ep.printStackTrace();
                        showToast_UI(mCtx.getString(R.string.exception_msg, ep.getError().getMessage()), OverlayDialogToast.DURATION_LONG);
                    } catch (Exception e) {
                        e.printStackTrace();
                        showToast_UI(mCtx.getString(R.string.exception_msg, e.getMessage()), OverlayDialogToast.DURATION_LONG);
                    }
                }

                @Override
                public void performActionWhenNothingFocused() {
                    Ln.w("000: No focused node found!");
                    if (retryCount >= MAX_REPEAT_WHILE_NODE_NOT_FOUND) {
                        Ln.w("NOT repeating!");
                        return;
                    }

                    if (retryCount == CLICK_NODE_WHEN_NODE_NOT_FOUND_REPEAT_COUNT) {
                        //try to click it
                        Ln.w("OOO: CLICKING IT");
                        clickNodeById(nodeId);
                    }


                    mOversecAccessibilityService.performNodeAction(new OversecAccessibilityService.PerformNodeAction() {

                        boolean found = false;

                        @Override
                        public void onNodeScanned(AccessibilityNodeInfo node) {

                            if (node.isFocused() && node.hashCode() == nodeId) {
                                found = true;
                                doEncrypt(params, nodeId, actionIntent, skbWasVisible, retryCount + 1, saveParams, addLink, innerPadding, packagename);
                            }
                        }

                        @Override
                        public void onScrapeComplete() {
                            if (!found) {
                                mUiHandler.sendMessageDelayed(
                                        Message.obtain(mUiHandler, WHAT_DO_ENCRYPT, new DoEncryptParams(
                                                params, nodeId, actionIntent, skbWasVisible, retryCount + 1, saveParams, addLink, innerPadding, packagename)), DELAY_REPEAT_WHILE_NODE_NOT_FOUND);
                            }
                        }
                    });
                }
            });
        }
    }

    private void onScreenOn_UI() {
        resetUserInteractionStuff_UI("SCREEN ON", false);
        if (mOversecAccessibilityService != null) {
            mOversecAccessibilityService.sendScrapeAllMessage("ACTION_SCREEN_ON", null);
        }
    }


    private void onScreenOff_UI() {
        removeOverlayDecrypt(true); //safety measure. If nothing is shown to the user, better GC the view nodes which contain decrypted text
        if (MainPreferences.INSTANCE.isPanicOnScreenOff(mCtx)) {
            panic_UI();
        }
    }

    public void resetUserInteractionStuff_UI(String reason, boolean stop) {
        checkUiThread();
        Ln.d(" resetUserInteractionStuff_UI reason=%s", reason);
        mPendingUserInteractions.clear();
        mPendingUserInteractionsImmediate.clear();
        if (stop) {
        } else {
            mUserInteractionDialogConfirmed = false;
        }
    }

    public void clearIgnoredTexts() {
        mUiHandler.sendEmptyMessage(WHAT_CLEAR_IGNORED_TEXTS);
    }

    private void clearIgnoredTexts_UI() {
        checkUiThread();
        mIgnoredSimpleKeyTexts.clear();
    }

    public void addUserInteractionRequired_UI(String encoded, boolean startProcessing) {
        checkUiThread();
        // Ln.d(" addUserInteractionRequired_UI: encode" + encoded);
        if (!mPendingUserInteractions.contains(encoded)) {
            mPendingUserInteractions.add(encoded);
        }
        if (startProcessing) {
            processPendingEncodedTextsRequiringUserInteraction_UI();
        }
    }

    private void processPendingEncodedTextsRequiringUserInteraction_UI() {
        checkUiThread();

        if (PendingIntentFiringBlankActivity.isShowing()) {
            return;
        }

        if (mPendingUserInteractions.size() > 0) {
            final String origText = mPendingUserInteractions.pop();

            //check that user interaction is still required
            Outer.Msg msg = XCoderFactory.Companion.getInstance(mCtx).decode(origText);
            if (msg != null) {
                if (!mPendingUserInteractionsImmediate.contains(origText)) {

                    if (mIgnoredSimpleKeyTexts.containsKey(origText)) {
                        mUiHandler.sendEmptyMessage(WHAT_PROCESS_REMAINING_PENDING_USERINTERACTIONS);
                        return;
                    }
                }
                executeDecryptUserInteractionRequired_UI(origText, null);
            }
        }
    }

    private void executeDecryptUserInteractionRequired_UI(final String tryToDecryptThisAgainToGetActualPendingIntent, Intent actionIntent) {
        checkUiThread();
        mPendingUserInteractionsImmediate.remove(tryToDecryptThisAgainToGetActualPendingIntent);

        Ln.d(" executeDecryptUserInteractionRequired_UI...");
        try {
            //decryption might have been succesful in the meantime, so check cache first

            //we have to prevent other threads from performing a decrypt operation until we have succesfully fired the pendingintent
            BaseDecryptResult dec = mCryptoHandlerFacade.decryptWithLock(tryToDecryptThisAgainToGetActualPendingIntent, actionIntent);
            if (dec.isOk()) {
                mEncryptionCache.put(tryToDecryptThisAgainToGetActualPendingIntent, dec);
            }
            mUiHandler.sendEmptyMessage(WHAT_PROCESS_REMAINING_PENDING_USERINTERACTIONS);

            Ln.d(" executeDecryptUserInteractionRequired_UI... NOT REQUIRED ANY MORE, put in cache");
        } catch (final UserInteractionRequiredException e) {
            Ln.d(" executeDecryptUserInteractionRequired_UI...NOW REALLY EXECUTING THE PENDING INTENT");

            if (!mIgnoreTextDialogActivityIsShowing) {

                boolean showImmediate = mDb.isShowUserInteractionDialogsImmediately(mUiThreadVars.getCurrentPackageName()) || mUserInteractionDialogConfirmed;

                if (mPendingUserInteractionsImmediate.contains(tryToDecryptThisAgainToGetActualPendingIntent)) {
                    showImmediate = true;
                }


                if (showImmediate) {
                    final Outer.Msg msg = XCoderFactory.Companion.getInstance(mCtx).decode(tryToDecryptThisAgainToGetActualPendingIntent);
                    if (msg != null) {
                        PendingIntentFiringBlankActivity.fire(mCtx, e.getPendingIntent(), 0, new PendingIntentFiringBlankActivity.PendingIntentResultCallback() {

                            @Override
                            public void onResultFromPendingIntentActivity(int requestCode, int resultCode, Intent data) {

                                if (msg.hasMsgTextSymSimpleV0()) {
                                    if (resultCode == Activity.RESULT_OK) {
                                        //there might be another UIRQ coming, so just try again
                                        executeDecryptUserInteractionRequired_UI(tryToDecryptThisAgainToGetActualPendingIntent, data);
                                    } else if (resultCode == Activity.RESULT_FIRST_USER) {
                                        //  Ln.d("  cancelled a SIMPLE dialog with CANCEL ALL: , setting mSimpleSymCancelAll to true");
                                        mIgnoredSimpleKeyTexts.put(tryToDecryptThisAgainToGetActualPendingIntent, DUMMY);
                                    }
                                } else {
                                    if (resultCode == Activity.RESULT_OK) {
                                        //there might be another UIRQ coming, so just try again
                                        executeDecryptUserInteractionRequired_UI(tryToDecryptThisAgainToGetActualPendingIntent, data);
                                    } else {
                                        // Ln.w(" PendingIntent activity was cancelled");
                                        mIgnoreTextDialogActivityIsShowing = true;
                                        //need a delay, otherwise the soft keyboard somehow loses connection to the underlying app
                                        mUiHandler.sendMessageDelayed(
                                                mUiHandler.obtainMessage(WHAT_SHOW_IGNORE_TEXT_DIALOG, tryToDecryptThisAgainToGetActualPendingIntent),
                                                500);
                                    }
                                }
                            }


                        });
                    }
                } else {
                    mOverlays.showDialogOverlayUserInteractionRequired(tryToDecryptThisAgainToGetActualPendingIntent);
                }
            }
        }
    }

    public void onUserInteractionDialogConfirmed_UI(boolean ok, String tryToDecryptThisAgainToGetActualPendingIntent) {
        dismissOverlayDialogIRQ();
        if (ok) {
            mUserInteractionDialogConfirmed = true;
            addUserInteractionRequired_UI(tryToDecryptThisAgainToGetActualPendingIntent, true);
            //mUiHandler.sendEmptyMessage(WHAT_PROCESS_REMAINING_PENDING_USERINTERACTIONS);
        } else {
            resetUserInteractionStuff_UI("userinteractiondialog cancelled", true);
        }
    }

    public void toggleInfoMode(boolean b) {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_TOGGLE_INFOMODE, 0, 0, b));
    }

    private void toggleInfoMode_UI(boolean state, int retryCnt) {
        checkUiThread();

        if (state) {
            //we might have been called from notification, so need to wait until we're out of the systemUI and decrypt overlay has been added again
            if (!mOverlays.isDecryptOverlayAdded() && retryCnt < 5) {
                mUiHandler.sendMessageDelayed(Message.obtain(mUiHandler, WHAT_TOGGLE_INFOMODE, retryCnt + 1, 0, true), 300);
                return;
            }

            //check we have something to show
            if (!mOverlays.hasVisibleEncryptedNodes(false)) {
                showToast_UI(mCtx.getString(R.string.toast_no_info_nodes), OverlayDialogToast.DURATION_SHORT);
                return;
            }
        } else {
            mPendingUserInteractions.clear();
            mEncryptionCache.clear(EncryptionCache.CLEAR_REASON.INFOMODE_LEFT, null);
            mOverlays.refreshDecrypt(null);
        }

        mOverlays.toggleInfoMode(state);
        showOrUpdateNotification_UI();

        mOversecAccessibilityService.sendScrapeAllMessage("toggleInfoMode_UI", null);
    }

    public void showToast_UI(String msg, long duration) {
        checkUiThread();

        mUiHandler.removeMessages(WHAT_HIDE_TOAST);
        mOverlays.showToast(msg);
        if (duration > 0) {
            mUiHandler.sendEmptyMessageDelayed(WHAT_HIDE_TOAST, duration);
        }
    }

    public boolean isInfoMode() {
        return mOverlays.isInfoMode();
    }

    public void startPreemptiveRefresh_UI() {
        if (mOversecAccessibilityService != null) {
            mOversecAccessibilityService.startPreemptiveRefresh(mUiThreadVars.getCurrentPackageName());
        }
    }

    private class SetTextParams {
        int nodeId;
        String newText;
        boolean bringUpSkb;
        boolean repeatIfFocusedNodeNotFound;
        int retryCount;

        public SetTextParams(int nodeId, String newText, boolean bringUpSkb, boolean repeatIfFocusedNodeNotFound, int retryCount) {
            this.nodeId = nodeId;
            this.newText = newText;
            this.bringUpSkb = bringUpSkb;
            this.repeatIfFocusedNodeNotFound = repeatIfFocusedNodeNotFound;
            this.retryCount = retryCount;
        }
    }

    public void setText_UI(SetTextParams p) {
        checkUiThread();

        setText_UI(p.nodeId, p.newText, p.bringUpSkb, p.retryCount);
    }

    public void setText_UI(final int nodeId,
                           final String newText,
                           final boolean bringUpSkb,
                           final int retryCount) {
        checkUiThread();

        OversecAccessibilityService aOversecAccessibilityService = mOversecAccessibilityService;
        if (aOversecAccessibilityService != null) {
            aOversecAccessibilityService.performActionOnFocusedNode(
                    new SetTextAction(nodeId, newText, bringUpSkb, retryCount, null));
            aOversecAccessibilityService.sendScrapeAllMessage("setText_UI", null);
        }
    }

    private class SetTextAction implements OversecAccessibilityService.PerformFocusedNodeAction {
        private final AbstractEncryptionParams params;
        int nodeId;
        String newText;
        boolean bringUpSkb;
        int retryCount;

        public SetTextAction(int nodeId, String newText, boolean bringUpSkb, int retryCount, AbstractEncryptionParams params) {
            this.nodeId = nodeId;
            this.newText = newText;
            this.bringUpSkb = bringUpSkb;
            this.retryCount = retryCount;
            this.params = params;
        }

        @Override
        public void performAction(AccessibilityNodeInfo node) {
            Ln.d("OOO: STA performAction");

            if (node.hashCode() != nodeId) {
                Ln.w("OOO: STA unexpected node id!");
                if (retryCount < MAX_REPEAT_WHILE_NODE_NOT_FOUND) {
                    mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SET_TEXT,
                            new SetTextParams(nodeId, newText, bringUpSkb, false, retryCount + 1)));
                }
                return;
            }


            if (bringUpSkb) {
                bringUpSkb(node);
            }

            if (newText != null) {
                boolean res = false;

                //NOTE: Regarding input in WebView
                // ACTION_SET_TEXT does not work (on <=N), but it might work in future versions O ?
                // ACTION_PASTE works but ACTION_SET_SELECTION doesn't work as well
                // if in a future version ACTION_SET_SELECTION would work, we could work around with ACTION_PASTE

                //to enable detection of the WebView as Input, see comments flagged as "WebViewXyzzy" in Tree.java

                //Note: unitl then, ppl need to use the "compose" Button-
                // which could possibly be improved a bit by automatically pasting the clipboard after encoding -
                //but we would have to make sure that the target field is EMPTY somehow ...

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    Bundle arguments = null;
                    arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
                    res = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                } else {
                    int charCount = getNodeText(node).length();
                    ClipData myClip = ClipData.newPlainText("", newText);
                    mCbm.setPrimaryClip(myClip);
                    Bundle arguments = new Bundle();
                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, charCount);
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments);


//                    Bundle arguments = new Bundle();
//                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
//                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 0);
//                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,arguments);
//                    SystemClock.sleep(200);
//
//                    arguments = new Bundle();
//                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
//                            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
//                    arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
//                            true);
//                    node.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
//                            arguments);


                    // SystemClock.sleep(200);
                    res = node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    SystemClock.sleep(200);
                    myClip = ClipData.newPlainText("", "");
                    mCbm.setPrimaryClip(myClip);
                }
                if (res) {
                    if (params != null) {
                        mUiThreadVars.setUserSelectedEncryptionParams(node.getPackageName().toString(), params, newText);
                    }
                }
                if (!res) {
                    //something went wrong,

                    //show a message to the user (failed to enter xxx-encoded text into target app "xyz"), please try to user another encoding for this app!
                    showTooltip_UI(null, mCtx.getString(R.string.tooltip_settextfailed), null, Help.ANCHOR.settextfailed, true);
                }
            }
        }

        @Override
        public void performActionWhenNothingFocused() {
            Ln.w("OOO: STA no focused node found!");
            if (retryCount > MAX_REPEAT_WHILE_NODE_NOT_FOUND) {
                Ln.w("NOT repeating");
                return;
            }

            clickNodeById(nodeId);

            mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_SET_TEXT,
                    new SetTextParams(nodeId, newText, bringUpSkb, false, retryCount + 1)));
        }
    }

    private void clickNodeById(final int nodeId) {
        //try to click the node, this at least required for skype!
        mOversecAccessibilityService.performNodeAction(new OversecAccessibilityService.PerformNodeAction() {

            boolean mFound = false;

            @Override
            public void onNodeScanned(AccessibilityNodeInfo node) {

                if (node.hashCode() == nodeId) {
                    mFound = true;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                }
            }

            @Override
            public void onScrapeComplete() {
                if (!mFound) {
                    Ln.w("Tried to click node %s but couldn't find it!", nodeId);
                }
            }
        });
    }

    private void bringUpSkb(AccessibilityNodeInfo node) {
        //note: bringing up SKB works only for >= Lollipop,
        //kitkat and such will have to live with the SKB going away when back from encryption params and such
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            //this will bring up the SLB only from Lollipop onwards, see http://stackoverflow.com/questions/26405371/android-accessibility-service-focus-edittext
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    public void checkUiThread() {
        if (CHECK_THREADS && Thread.currentThread() != mHandlerThread) {
            throw new RuntimeException("ILLEGAL THREAD " + Thread.currentThread().getName());
        }
    }

    public void checkMainThread() {
        if (CHECK_THREADS && Thread.currentThread() != mMainThread) {
            throw new RuntimeException("ILLEGAL THREAD " + Thread.currentThread().getName());
        }
    }

    public int dipToPixels(int dip) {
        Resources r = getCtx().getResources();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, r.getDisplayMetrics());
    }

    private class TemporaryHideParams {
        String packageName;
        Boolean hide;

        public TemporaryHideParams(String packageName, Boolean hide) {
            this.packageName = packageName;
            this.hide = hide;
        }
    }

    @Override
    public List<PadderContent> getAllPaddersSorted() {
        return PadderDb.getInstance(mCtx).getAllValues();
    }


    @Override
    public void doIfFullVersionOrShowPurchaseDialog(final Activity activity, final Runnable okRunnable, final int requestCode) {
        IabUtil.getInstance(mCtx).doIfFullOrShowPurchaseDialog(activity, okRunnable, requestCode);
    }


    @Override
    public void doIfFullVersionOrShowPurchaseDialog(final Fragment fragment, final Runnable okRunnable, final int requestCode) {
        IabUtil.getInstance(mCtx).doIfFullOrShowPurchaseDialog(fragment, okRunnable, requestCode);
    }


    public void removeUpgradeDialog() {
        mUiHandler.sendEmptyMessage(WHAT_REMOVE_DIALOG_UPGRADE);
    }


    public void removeUpgradeDialog_UI() {
        checkUiThread();
        mOverlays.removeButtonUpgrade();
        mOverlays.removeDialogOverlayUpgrade();
    }

    public void showUpgradeDialog_UI() {
        checkUiThread();

        mOverlays.showDialogUpgrade();
    }

    public void setLastWindowStateChangedClassName(CharSequence className, String packageName) {
        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_LAST_WINDOW_STATE_CHANGE_CLASSNAME, new Object[]{className, packageName}));
    }

    public void setLastWindowStateChangedClassName_UI(CharSequence className, String packageName) {
        mUiThreadVars.setLastWindowStateChangedClassName(className, packageName);
    }

    public void setLastSavedUserSelectedEncryptionParams(AbstractEncryptionParams encryptionParams, String packagename) {
        mUiThreadVars.setLastSavedUserSelectedEncryptionParams(encryptionParams, packagename);
    }


    @Override
    public AbstractEncryptionParams getBestEncryptionParams(String packageName) {
        //TODO: THIS will be called from MAIN thread (start of EncryptionParamsActivity), somehow need to synchronize!
        return mUiThreadVars.getBestEncryptionParams(packageName);
    }


    public void onDecryptResultShownOnScreen(BaseDecryptResult tdr, String packageName) {
        checkUiThread();
        if (tdr != null && tdr.isOk()) {

            mUiThreadVars.setLastScrapedDecryptResult(tdr, packageName);

        }
    }

    public boolean isNextEncryptionWouldUsePreviousEncryptParams(String packagename) {
        return mUiThreadVars.hasUserSelectedEncryptionParams(packagename);
    }

    public AbstractEncryptionParams getLastSavedUserSelectedEncryptionParams(String packagename) {
        return mUiThreadVars.getLastSavedUserSelectedEncryptionParams(packagename);
    }


    public static final CharSequence getNodeText(final AccessibilityNodeInfo node) {
        CharSequence r = AccessibilityNodeInfoUtils.getNodeText(node);
        return r == null ? "" : r;
    }

    public void doEncryptAndPutToClipboard(String s, String packagename, Intent data, byte[] innerPadding) {

        mUiHandler.sendMessage(Message.obtain(mUiHandler, WHAT_ENCRYPT_CLIPBOARD, new Object[]{s, packagename, data, innerPadding}));
    }

    public void doEncryptAndPutToClipboard_UI(final String s, final String packagename, Intent data, byte[] xinnerPadding) {
        checkUiThread();

        if (xinnerPadding == null) {
            xinnerPadding = new byte[0];
            int maxInnerPadding = mDb.getMaxInnerPadding(packagename);
            if (maxInnerPadding > 0) {
                int p = (int) (Math.random() * maxInnerPadding);
                xinnerPadding = KeyUtil.INSTANCE.getRandomBytes(p);
            }
        }
        final byte[] innerPadding = xinnerPadding;

        final AbstractEncryptionParams ep =
                mUiThreadVars.getBestEncryptionParams(packagename);
        if (ep == null) {
            showToast_UI(mCtx.getString(R.string.exception_msg, "D'Oh"), OverlayDialogToast.DURATION_LONG);
        } else {
            try {

                String enc = mCryptoHandlerFacade.encrypt(
                        ep,
                        s,
                        mDb.isAppendNewLines(packagename),
                        innerPadding,
                        packagename,
                        data);

                ClipData myClip = ClipData.newPlainText("", enc);
                mCbm.setPrimaryClip(myClip);

                showTooltip_UI(null,
                        mCtx.getString(R.string.tooltip_paste_clipboard),
                        mCtx.getString(R.string.tooltipid_paste_clipboard),
                        Help.ANCHOR.paste_clipboard,
                        true);
            } catch (UserInteractionRequiredException ue) {
//                if (PendingIntentFiringBlankActivity.isShowing()) {
//                    Ln.w("About to show UIRQ, but was already active!");
//                    return;
//                }

                removeOverlayDecrypt_UI(false);
                PendingIntentFiringBlankActivity.fire(mCtx, ue.getPendingIntent(), 0, new PendingIntentFiringBlankActivity.PendingIntentResultCallback() {

                    @Override
                    public void onResultFromPendingIntentActivity(int requestCode, int resultCode, Intent data) {
                        if (resultCode == Activity.RESULT_OK) {
                            doEncryptAndPutToClipboard(s, packagename, data, innerPadding);
                        } else {
                            Ln.w("user cancelled pendingintent activity");
                        }
                    }

                });
            } catch (Exception e) {
                e.printStackTrace();
                showToast_UI(mCtx.getString(R.string.exception_msg, e.getMessage()), OverlayDialogToast.DURATION_LONG);
            }
        }
    }

    @Override
    public boolean isDbSpreadInvisibleEncoding(String packagename) {
        return mDb.isSpreadInvisibleEncoding(packagename);
    }

    @Override
    public void clearEncryptionCache() {
        mEncryptionCache.clear(EncryptionCache.CLEAR_REASON.CLEAR_CACHED_KEYS, null);
    }

    @Override
    public void putInEncryptionCache(String encText, BaseDecryptResult tdr) {
        mEncryptionCache.put(encText, tdr);
    }

    @Override
    public BaseDecryptResult getFromEncryptionCache(String encText) {
        return mEncryptionCache.get(encText);
    }

    public void addIgnoredText(String txt) {
        mIgnoredSimpleKeyTexts.put(txt, DUMMY);
    }

    public boolean isTextIgnored(String txt) {
        return mIgnoredSimpleKeyTexts.containsKey(txt);
    }
}

