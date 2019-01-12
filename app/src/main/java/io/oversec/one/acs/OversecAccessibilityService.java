package io.oversec.one.acs;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import io.oversec.one.Core;
import io.oversec.one.EncryptionCache;
import io.oversec.one.acs.util.AccessibilityNodeInfoUtils;
import io.oversec.one.acs.util.Bag;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.LoggingConfig;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.ui.EncryptionInfoActivity;
import io.oversec.one.db.Db;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;
import io.oversec.one.util.WrappedWindowManager;
import roboguice.util.Ln;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NOTE:
 * <p>
 * some things to know about androids AccessiblityService:
 * <p>
 * ** WINDOW_CONTENT_CHANGED and change type SUBTREE  invalidates the internal node cache
 * ** prefetching descendants works recursively, but will always fetch max. 50 nodes,
 * ** node.refresh -> does NOT fetch descendants
 * ** getRootInActiveWindow -> DOES fetch descendants
 * ** node.getChild -> DOES fetch descendants
 * ** getParent -> fetches descendants, siblings and predecessors!
 * <p>
 */
@SuppressLint("Registered")
public class OversecAccessibilityService extends AccessibilityService implements IDecryptOverlayLayoutParamsChangedListener {
    private static final long DELAY_HOUSEKEEPING = 500;
    private static final long DELAY_SUBTREE = 150;

    private static final int WHAT_SCRAPE_SUBTREE = 2;
    private static final int WHAT_REFRESH_NODE = 3;
    private static final int WHAT_HOUSEKEEPING = 4;
    private static final int WHAT_FORCE_LIST_REFRESH = 5;
    private static final int WHAT_SCRAPE_ALL_NO_ACTION = 6;
    private static final int WHAT_SCRAPE_ALL_WITH_ACTION = 7;

    private static final int PREEMPTIVE_REFRESH_COUNT = 10;
    private static final int PREEMPTIVE_REFRESH_DELAY = 200;

    private static final boolean HANDLER_IN_BG_THREAD = true;
    HandlerThread mHandlerThread = HANDLER_IN_BG_THREAD ? new HandlerThread("ACSHandler") : null;
    private Handler mHandler;

    private Core mCore;

    private WrappedWindowManager mWm;
    private DisplayMetrics mMetrics = new DisplayMetrics();
    private Rect mWindowBoundsInScreen = new Rect();
    private IME_STATUS mImeStatus;
    private InputMethodManager mImeManager;


    private Tree mTree;
    Bag<NodeAndFlag> mRefreshNodeBag = new Bag<>(50);
    Bag<NodeAndFlag> mScrapeSubtreeBag = new Bag<>(50);

    private Db mDb;
    private CryptoHandlerFacade mCryptoHandlerFacade;
    private int mLastRootNodeKey;
    private int mLastMonitoredEventTypes;

    private boolean mLastIncludeNotImporantViews;
    private boolean mLastRequestEnhancedWebAccessibility;
    private boolean mIgnoreNextWINDOW_STATE_CHANGED;


    public boolean isImeFullscreen() {
        return mImeStatus == IME_STATUS.FULLSCREEN;
    }

    public boolean isImeVisible() {
        return mImeStatus != IME_STATUS.HIDDEN;
    }

    public void startPreemptiveRefresh(String packageName) {
        if (mDb.isHqScrape(packageName)) {
            for (int i = 0; i < PREEMPTIVE_REFRESH_COUNT; i++) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(WHAT_SCRAPE_ALL_NO_ACTION,
                        new Object[]{"preemptiveRefresh (" + i + "/" + PREEMPTIVE_REFRESH_COUNT + ")", null})

                        , (i + 1) * PREEMPTIVE_REFRESH_DELAY);
            }
        }
    }

    @Override
    public void onDecryptOverlayLayoutParamsChanged(String packagename) {

        sendScrapeAllMessage("onDecryptOverlayLayoutParamsChanged", null);

        setMonitorEventTypesAll(); //to update filtering for Click/LongClick
    }

    public boolean isEditableEditTextFocussed() {
        boolean res = false;
        AccessibilityNodeInfo anode = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            anode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        } else {
            anode = findFocus_PreLollipop();
        }
        if (anode != null) {
            if (anode.isEditable() && anode.isEnabled()) {
                if (mTree.isAEditText(anode)) {
                    res = true;
                }
            }
            anode.recycle();
        }
        return res;
    }

    public void clearMainHandler() {
        mHandler.removeMessages(WHAT_HOUSEKEEPING);
        mHandler.removeMessages(WHAT_FORCE_LIST_REFRESH);
        mHandler.removeMessages(WHAT_REFRESH_NODE);
        mHandler.removeMessages(WHAT_SCRAPE_ALL_NO_ACTION);
        mHandler.removeMessages(WHAT_SCRAPE_ALL_WITH_ACTION);
        mHandler.removeMessages(WHAT_SCRAPE_SUBTREE);
        //mIgnoreNextWINDOW_STATE_CHANGED = true;
    }


    public enum IME_STATUS {
        HIDDEN, HALFSCREEN, FULLSCREEN
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mCore.setOversecAccessibilityService(this);

        AccessibilityServiceInfo params = getServiceInfo();

        mLastMonitoredEventTypes = params.eventTypes;

        setMonitorEventTypesAll();
    }


    private void setMonitorEventTypes(int eventTypes, boolean includeNotImporantViews, boolean requestEnhancedWebAccessibility) {
        if (eventTypes != mLastMonitoredEventTypes || includeNotImporantViews != mLastIncludeNotImporantViews || requestEnhancedWebAccessibility != mLastRequestEnhancedWebAccessibility) {

            Ln.d("SERVICE: mLastMonitoredEventTypes=%s, eventTypes=%s", mLastMonitoredEventTypes, eventTypes);

            mLastMonitoredEventTypes = eventTypes;
            mLastIncludeNotImporantViews = includeNotImporantViews;
            mLastRequestEnhancedWebAccessibility = requestEnhancedWebAccessibility;
            AccessibilityServiceInfo params = getServiceInfo();
            if (params == null) {
                Ln.w("DAMNIT, somethimes this shit happens: serviceparams returned are null!");
            } else {
                params.eventTypes = mLastMonitoredEventTypes;
                if (includeNotImporantViews) {
                    params.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
                } else {
                    params.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
                }

                if (mLastRequestEnhancedWebAccessibility) {
                    params.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
                } else {
                    params.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
                }

                setServiceInfo(params);
            }
        }
    }


    public void setMonitorEventTypesAll() {
        Ln.d("SERVICE: setMonitorEventTypesAll");

        int mask = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;

        String packagename = mCore.getCurrentPackageName();

        if (packagename == null || mDb.isShowInfoOnTap(packagename)) {
            Ln.d("SERVICE: setMonitorEventTypesAll adding TYPE_VIEW_CLICKED");
            mask |= AccessibilityEvent.TYPE_VIEW_CLICKED;
        }
        if (packagename == null || mDb.isShowInfoOnLongTap(packagename) || mDb.isToggleEncryptButtonOnLongTap(packagename)) {
            Ln.d("SERVICE: setMonitorEventTypesAll adding TYPE_VIEW_LONG_CLICKED");
            mask |= AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
        }

        boolean includeNotImportantViews = packagename == null ? true : mDb.isIncludeNonImportantViews(packagename);

        setMonitorEventTypes(mask, includeNotImportantViews, true);


    }

    public void setMonitorEventTypesMinimal() {
        Ln.d("SERVICE: setMonitorEventTypesMinimal");
        setMonitorEventTypes(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, false, false);


    }

    public void setMonitorEventTypesNone() {
        Ln.d("SERVICE: setMonitorEventTypesMinimal");
        setMonitorEventTypes(0, false, false);


    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCore = Core.getInstance(this);
        mCryptoHandlerFacade = mCore.getEncryptionHandler();
        mDb = mCore.getDb();
        mCore.addDecryptOverlayLayoutParamsChangedListenerMainThread(this);
        mTree = new Tree(mCore.getCtx());

        mWm = WrappedWindowManager.get(this);


        mImeManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

//
//        hmm, try running stuff in background thread....
//        #
//        maybe only run *some* stuff in background (like getRootInActiveWindow)
//        #
//        if that doesn't work. maybe mark / throttle calls,
//        if calls are too frequent yield some time to the system ???

        //TODO: see HANDLER_IN_BG_THREAD above - revert to whatever is found to be more stable
        if (mHandlerThread != null) {
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper(), mCallback);
        } else {
            mHandler = new Handler(Looper.getMainLooper(), mCallback);
        }

    }


    @Override
    public boolean onUnbind(Intent i) {
        mCore.setOversecAccessibilityService(null);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        return false;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mCore.onConfigurationChanged(newConfig);
    }


    private Map<String, Boolean> mImePackageNameCache = Collections.synchronizedMap(new HashMap());

    private boolean

    isAnIme(String packageName) {
        Boolean b = mImePackageNameCache.get(packageName);
        if (b != null) {
            return b;
        }
        List<InputMethodInfo> inputMethods = mImeManager.getEnabledInputMethodList();
        for (InputMethodInfo imi : inputMethods) {
            if (packageName.equals(imi.getPackageName())) {
                mImePackageNameCache.put(packageName, true);
                return true;
            }
        }
        mImePackageNameCache.put(packageName, false);
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent arg0) {
        try {
            if (LoggingConfig.INSTANCE.getLOG()) {
                Ln.d("EVENT: %s", eventToString(arg0));
            }


            //See https://github.com/oversecio/oversec_crypto/issues/37
            //Android accessibility API is posting some weird events in Android 7.x
            //Not sure why, but for now we just filter them out like this:
            if (getPackageName().equals(arg0.getPackageName())) {
                AccessibilityNodeInfo ni = arg0.getSource();
                try {
                    if (ni != null && ni.getChildCount() == 0) {
                        if ("android.view.ViewGroup".equals(arg0.getClassName())) {
                            Ln.d("EVENT XQQ: dropped a bogus event ^^^^^");
                            return;
                        }
                    }
                } finally {
                    if (ni != null) {
                        ni.recycle();
                    }
                }
            }


            int type = arg0.getEventType();
            String packageName = arg0.getPackageName() == null ? null : arg0.getPackageName().toString();


            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (Core.PACKAGE_SYTEMUI.equals(arg0.getPackageName())) {
                    if (arg0.getClassName() != null && arg0.getClassName().toString().contains("Task")) {
                        //Most likely tasks-view has been brought up
                        mCore.removeNotification();
                    }
                }
            }

            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                mCore.setLastWindowStateChangedClassName(arg0.getClassName(), packageName);
                if (packageName != null) {
                    Ln.d("SERVICE: new event, packageName= %s", packageName);
                    if (mDb.isAppEnabled(packageName)
                            //|| Core.PACKAGE_SYTEMUI.equals(packageName)
                            || getPackageName().equals(packageName)
                            || OpenKeychainConnector.PACKAGE_NAME.equals(packageName)
                            || isAnIme(packageName)) {
                        setMonitorEventTypesAll();
                    } else {
                        setMonitorEventTypesMinimal();
                    }
                }
            }


            if (packageName == null || isAnIme(packageName)) {
                if (type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                    Ln.d("EVENT: Ignoring event from null or IME package %s ", packageName);
                    return;
                }
            }

            boolean ours = mCore.getDb().isShowDecryptOverlay(packageName);

            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (ours) {
                    Ln.d("SKRAPE: SEND SCRAPE ALL MESSAGE on package change");
                    if (mIgnoreNextWINDOW_STATE_CHANGED) {
                        mIgnoreNextWINDOW_STATE_CHANGED = false;
                    } else {
                        sendScrapeAllMessage("WINDOW_STATE_CHANGED", null);
                    }
                } else {
                    Ln.d("SKRAPE: IGNORING accessibility event for package %s", packageName);
                    clearFocusedNode_PreLollipop();
                    Core.getInstance(this).onAcsScrapeCompleted(packageName, null);
                    mCore.getEncryptionCache().clear(EncryptionCache.CLEAR_REASON.PACKAGE_CHANGED, packageName);
                    return;
                }
                return;

            } else if (arg0.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) { //Note: available only in lollipop and onwards
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

                    sendScrapeAllMessage("WINDOWS_CHANGED", null);


                    AccessibilityWindowInfo www = getWindowOfTypeIme();
                    if (www != null) {
                        www.getBoundsInScreen(mWindowBoundsInScreen);
                        mWm.getDefaultDisplay().getMetrics(mMetrics);
                        if (mWindowBoundsInScreen.height() > mMetrics.heightPixels * 0.75) {
                            onImeDetected(IME_STATUS.FULLSCREEN);
                        } else {
                            onImeDetected(IME_STATUS.HALFSCREEN);
                        }
                    } else {
                        onImeDetected(IME_STATUS.HIDDEN);
                    }
                }

            } else if (mCore.getDb().isAppEnabled(packageName)
                    ) {

                if (type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED || type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    AccessibilityNodeInfo n = arg0.getSource();

                    if (n != null) {


                        if (mTree.isAEditText(n)) {
                            if (type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                                if (mDb.isToggleEncryptButtonOnLongTap(packageName)) {
                                    mDb.setShowEncryptButton(packageName, !mDb.isShowEncryptButton(packageName));
                                    mCore.updateButtonVisibilities();

                                }
                            }
                        } else {
                            if (type == AccessibilityEvent.TYPE_VIEW_CLICKED && mDb.isShowInfoOnTap(packageName)
                                    ||
                                    type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED && mDb.isShowInfoOnLongTap(packageName)
                                    ) {

                                CharSequence enc = AccessibilityNodeInfoUtils.getNodeText(n);
                                if (!mTree.isATextView(n)) {
                                    enc = mTree.getFirstEncodedChildText(n);

                                } else {
                                    if (enc != null) {
                                        //check if this is encoded
                                        if (CryptoHandlerFacade.Companion.getEncodedData(this, enc.toString()) == null) {
                                            enc = null;
                                        }
                                    }
                                }

                                if (enc != null) {
                                    mCore.removeOverlayDecrypt(false);
                                    EncryptionInfoActivity.Companion.show(mCore.getCtx(), packageName, enc.toString(), null);
                                }
                            }
                        }
                        n.recycle();
                    }

                } else if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    if (LoggingConfig.INSTANCE.getLOG()) {
                        Ln.d("SCRAPE: VIEW_SCROLLED from %s to %s", arg0.getFromIndex(), arg0.getToIndex());
                    }
                    if (mDb.isHqScrape(arg0.getPackageName().toString())) {
                        sendScrapeSubtreeMessages(arg0);
                    } else {
                        triggerHousekeeping();
                    }


                } else if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                    if (LoggingConfig.INSTANCE.getLOG()) {
                        Ln.d("SCRAPE: VIEW_TEXT_CHANGED new text= %s", arg0.getText());
                    }
                    sendRefreshNodeMessages(arg0);
                } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    boolean hasCONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = false;
                    boolean hasCONTENT_CHANGE_TYPE_SUBTREE = false;
                    boolean hasCONTENT_CHANGE_TYPE_TEXT = false;
                    boolean hasCONTENT_CHANGE_TYPE_UNDEFINED = false;
                    int cct = -1;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        cct = arg0.getContentChangeTypes();
                        hasCONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0);
                        hasCONTENT_CHANGE_TYPE_SUBTREE = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0);
                        hasCONTENT_CHANGE_TYPE_TEXT = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0);
                        hasCONTENT_CHANGE_TYPE_UNDEFINED = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) != 0);

                    } else {
                        //on pre Kitkat we have to assume to worst ;)
                        hasCONTENT_CHANGE_TYPE_SUBTREE = true;
                    }
                    if (LoggingConfig.INSTANCE.getLOG()) {
                        Ln.d("SKRAPE: WINDOW_CONTENT_CHANGED  ContentChangeTypes: %s ", cct);
                        if (hasCONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) {
                            Ln.d("SKRAPE: CONTENT_CHANGE_TYPE_DESCRIPTION");
                        }
                        if (hasCONTENT_CHANGE_TYPE_SUBTREE) {
                            Ln.d("SKRAPE: CONTENT_CHANGE_TYPE_USUBTREE");
                        }
                        if (hasCONTENT_CHANGE_TYPE_TEXT) {
                            Ln.d("SKRAPE: CONTENT_CHANGE_TYPE_TEXT");
                        }
                        if (hasCONTENT_CHANGE_TYPE_UNDEFINED) {
                            Ln.d("SKRAPE: CONTENT_CHANGE_TYPE_UNDEFINED");
                        }
                    }
                    if (hasCONTENT_CHANGE_TYPE_SUBTREE || hasCONTENT_CHANGE_TYPE_TEXT) {
                        if (mDb.isHqScrape(arg0.getPackageName().toString())) {
                            sendScrapeSubtreeMessages(arg0);
                        } else {
                            triggerHousekeeping();
                        }

                    } else if (hasCONTENT_CHANGE_TYPE_UNDEFINED) {
                        //triggerRefresh(node, "WINDOW_CONTENT_CHANGED && hasCONTENT_CHANGE_TYPE_UNDEFINED");
                        triggerHousekeeping();
                    }

                }

            }
        } catch (SecurityException ex) {
            //this usually happens when switching users, so no case of concern
            Ln.w(ex);
        }
    }

    @Override
    public void onInterrupt() {

    }

    public void sendScrapeAllMessage(String reason, PerformNodeAction nodeAction) {
        if (nodeAction != null) {
            //remove any pending SCRAPE_ALL message without any action, they should be obsolete
            mHandler.removeMessages(WHAT_SCRAPE_ALL_NO_ACTION);
        }
        mHandler.sendMessage(mHandler.obtainMessage(nodeAction == null ? WHAT_SCRAPE_ALL_NO_ACTION : WHAT_SCRAPE_ALL_WITH_ACTION, new Object[]{reason, nodeAction}));
    }

    private void sendScrapeSubtreeMessages(AccessibilityEvent arg0) {
        sendScrapeSubtreeMessage(arg0.getSource(), null);
        int n = arg0.getRecordCount();
        for (int i = 0; i < n; i++) {
            sendScrapeSubtreeMessage(arg0.getRecord(i).getSource(), null);
        }
    }

    private void sendScrapeSubtreeMessage(AccessibilityNodeInfo node, PerformNodeAction nodeAction) {
        if (node == null) {
            Ln.w("SKRAPE: node == null!");
            return;
        }
        NodeAndFlag nf = new NodeAndFlag(node, nodeAction);

        //mark as cancelled all pending items
        synchronized (mScrapeSubtreeBag) {
            int n = mScrapeSubtreeBag.size();
            for (int i = 0; i < n; i++) {
                NodeAndFlag item = mScrapeSubtreeBag.get(i);
                if (item!=null && item.nodeHash == nf.nodeHash) {
                    item.cancelled = true;
                }
            }
        }


        mScrapeSubtreeBag.add(nf);
        mHandler.sendMessage(Message.obtain(mHandler, WHAT_SCRAPE_SUBTREE, nf));

        triggerHousekeeping();
    }

    private void triggerHousekeeping() {
        mHandler.removeMessages(WHAT_HOUSEKEEPING);
        mHandler.sendEmptyMessageDelayed(WHAT_HOUSEKEEPING, DELAY_HOUSEKEEPING);
    }

    private void sendRefreshNodeMessages(AccessibilityEvent arg0) {
        sendRefreshNodeMessage(arg0.getSource());
        int n = arg0.getRecordCount();
        for (int i = 0; i < n; i++) {
            sendRefreshNodeMessage(arg0.getRecord(i).getSource());
        }
    }


    private void sendRefreshNodeMessage(AccessibilityNodeInfo node) {
        if (node == null) {
            Ln.w("SKRAPE: dòh, got a null node!");
            return;
        }


        //TODO: optionally generally throttle (Delay subtree scrapes)

//        avoid excess subtree rescrapes triggered by the system.
//           they may arrive while the system is still scraping a previous event.

        NodeAndFlag nf = new NodeAndFlag(node, null);

        //mark as cancelled all pending items
        synchronized (mRefreshNodeBag) {
            int n = mRefreshNodeBag.size();
            for (int i = 0; i < n; i++) {
                NodeAndFlag item = mRefreshNodeBag.get(i);
                if (item.nodeHash == nf.nodeHash) {
                    item.cancelled = true;
                }
            }
        }

        mRefreshNodeBag.add(nf);
        if (mDb.isHqScrape(node.getPackageName().toString())) {
            mHandler.sendMessage(Message.obtain(mHandler, WHAT_REFRESH_NODE, nf));
        } else {
            mHandler.sendMessageDelayed(Message.obtain(mHandler, WHAT_REFRESH_NODE, nf), DELAY_SUBTREE);
        }


    }


    private void refreshNode_MAIN(AccessibilityNodeInfo node) {
        checkHandlerThread();
        if (LoggingConfig.INSTANCE.getLOG()) {
            Ln.d("SKRAPE: refreshNode node= %s", node.hashCode());
        }

        long t1 = System.currentTimeMillis();

        //no need to explicitly refresh, the ACS event shouldhave passed us the latest version already!
        //node.refresh();
        if (node.isFocused()) {
            checkFocusedNode_PreLollipop(node);
        }


        Tree.TreeNode treeNode = mTree.get(node);


        if (treeNode == null) {
            mTree.put(node);
        } else {
            treeNode.refresh(node);
        }


        long t2 = System.currentTimeMillis();
        Ln.d("SKRAPE refreshNode took %s", (t2 - t1));


    }

    private void scrapeSubtree_MAIN(AccessibilityNodeInfo node, String reason, PerformNodeAction nodeAction) {
        checkHandlerThread();

        if (LoggingConfig.INSTANCE.getLOG()) {
            Ln.d("SKRAPE: scrapeSubtree node=%s  reason=%s", node.hashCode(), reason);
        }
        long t1 = System.currentTimeMillis();

        node.refresh();
        checkFocusedNode_PreLollipop(node);


        Tree.TreeNode treeNode = mTree.get(node);
        if (treeNode != null) {
            mTree.removeSubtree(treeNode);

        } else {
            treeNode = mTree.put(node);

        }
        scrapeCompleteSubtree_MAIN(treeNode, node, nodeAction);


        long t2 = System.currentTimeMillis();
        Ln.d("SKRAPE scrapeSubtree took %s", (t2 - t1));

    }


    private void scrapeCompleteSubtree_MAIN(Tree.TreeNode treeNode, AccessibilityNodeInfo node, PerformNodeAction nodeAction) {
        checkHandlerThread();

        node.refresh();

        checkFocusedNode_PreLollipop(node);

        if (!node.isVisibleToUser()) {
            return;
        }

        int cc = node.getChildCount();

        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo child = node.getChild(i);


            if (child != null) {
                Tree.TreeNode childTreeNode = mTree.put(child);
                treeNode.addChild(childTreeNode);
                if (nodeAction != null) {
                    nodeAction.onNodeScanned(child);
                }
                scrapeCompleteSubtree_MAIN(childTreeNode, child, nodeAction);
                child.recycle();
            } else {
                Ln.d("SKRAPE: warning, couldn't get a child!");
                //TODO: one reason for this might be a too large binder transaction -> maybe at least give some feedback to the user
            }
        }

    }


    private void onImeDetected(IME_STATUS status) {
        Ln.d("IME: onImeDetected %s  (prev: %s)", status.name(), mImeStatus);
        if (mImeStatus != status) {
            mImeStatus = status;
            mCore.onImeStatusChanged(status);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private AccessibilityWindowInfo getWindowOfTypeIme() {
        try {
            List<AccessibilityWindowInfo> ww = getWindows();
            for (AccessibilityWindowInfo w : ww) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return w;
                }
            }
        } catch (java.lang.SecurityException ex) {
            //This can only happen when switching users!
            Ln.e(ex, "failed to getWindows!");
        }
        return null;
    }


    private AccessibilityNodeInfo mLastListRefreshNode;
    private Handler.Callback mCallback = new Handler.Callback() {


        @Override
        public boolean handleMessage(Message msg) {
            checkHandlerThread();

            switch (msg.what) {
                case WHAT_HOUSEKEEPING:
                    Ln.d("SKRAPE: Housekeeping!");
                    handleScrapeAll_MAIN("Housekeeping", null);
                    break;
                case WHAT_SCRAPE_ALL_NO_ACTION:
                case WHAT_SCRAPE_ALL_WITH_ACTION:
                    String why = (String) (((Object[]) msg.obj)[0]);
                    PerformNodeAction nodeAction = (PerformNodeAction) (((Object[]) msg.obj)[1]);
                    handleScrapeAll_MAIN(why, nodeAction);
                    break;
                case WHAT_SCRAPE_SUBTREE:
                    NodeAndFlag nf = (NodeAndFlag) msg.obj;
                    synchronized (mScrapeSubtreeBag) {
                        mScrapeSubtreeBag.remove(nf);
                    }
                    if (!nf.cancelled) {
                        scrapeSubtree_MAIN(nf.node, "WHAT_SCRAPE_SUBTREE", nf.nodeAction);
                        publishChanges_MAIN();
                    } else {
                        Ln.d("SKRAPE: SKIPPED a subtree scrape for node %s", nf.nodeHash);
                    }
                    if (mLastListRefreshNode != null) {
                        mLastListRefreshNode.recycle();
                    }
                    mLastListRefreshNode = nf.node;
                    //nf.node.recycle();

                    break;
                case WHAT_REFRESH_NODE:
                    mHandler.removeMessages(WHAT_FORCE_LIST_REFRESH);
                    NodeAndFlag nf2 = (NodeAndFlag) msg.obj;
                    synchronized (mRefreshNodeBag) {
                        mRefreshNodeBag.remove(nf2);
                    }
                    if (!nf2.cancelled) {
                        refreshNode_MAIN(nf2.node);
                        publishChanges_MAIN();
                    } else {
                        Ln.d("SKRAPE: SKIPPED a node refresh for node %s", nf2.nodeHash);
                    }
                    nf2.node.recycle();

                    break;
                case WHAT_FORCE_LIST_REFRESH:
                    try {
                        AccessibilityNodeInfo nn = AccessibilityNodeInfo.obtain(mLastListRefreshNode);
                        mLastListRefreshNode = null;
                        Ln.e("SKRAPE: sending forced list refresh");
                        sendScrapeSubtreeMessage(nn, null);
                    } catch (Exception ex) {
                        // Ln.e(ex);
                    }
                    break;
            }

            return false;
        }
    };

    private void handleScrapeAll_MAIN(String reason, PerformNodeAction nodeAction) {
        checkHandlerThread();

        mTree.clear();
        AccessibilityNodeInfo rootNode = null;
        try {
            rootNode = getRootInActiveWindow();
        } catch (Exception ex) {
            Ln.e(ex);
            return;
        }
        try {
            if (rootNode != null) {

                //fail-fast if this is not a package we're interested in
                CharSequence csPackageName = rootNode.getPackageName();
                if (csPackageName==null) return;
                String aPackageName = rootNode.getPackageName().toString();
                boolean ours = mCore.getDb().isShowDecryptOverlay(aPackageName);
                if (!ours) {
                    mCore.onAcsScrapeCompleted(aPackageName, null);
                    return;
                }

                findFocusedNodePreLollipop_startTransaction();
                if (LoggingConfig.INSTANCE.getLOG()) {
                    Ln.d("SKRAPE: FULL SCAN, root=%s", rootNode.hashCode());
                }

                mTree.addRootNode(rootNode);

                scrapeSubtree_MAIN(rootNode, "handleScrapeAll_MAIN-> " + reason, nodeAction);

                rootNode.recycle();

                findFocusedNodePreLollipop_commitTransaction();

                publishChanges_MAIN();


            } else {
                Ln.w("SKRAPE: getRootInActiveWindow returned null node!");
                clearFocusedNode_PreLollipop();
                //TODO: one reason for this might be a too large binder transaction -> maybe at least give some feedback to the user
                //TODO: or somehow scrape sub-nodes first in order to have separate smalle transactions?#
//            How to Prevent MAX binder transaction / max parcel overflow, Could happen when getting a list node with many immediate GPG text views (the listnode prefetches its children, and if all of them have a long pgp encoded text, this may just be too much for a single binder transaction)
//            size is 1 mb, let's say 50 nodes gives 20 k per node,   worst case every character is encoded with 4 bytes gives 5 k text!!!  -> might just be enough for pgp
//                    >hmmm, prefetching descendants works recursively, but will always fetch max. 50 nodes,
//            -> hopefully not needed but there's noreal way around it!other than using reflection to get the child node's IDs and then manually doing refresh

            }

        } finally {
            if (nodeAction != null) {
                nodeAction.onScrapeComplete();
            }
        }

    }

    private AccessibilityNodeInfo mFocusedNode_PreLollipop;
    private AccessibilityNodeInfo mFocusedNode_PreLollipop_INTRANSACTION;
    private Object mSEMAPHORE_FocusedNode_PreLollipop = new Object();

    private void clearFocusedNode_PreLollipop() {
        //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) return;
        //need to keep that anyway if we want to find focus in WebViews
        synchronized (mSEMAPHORE_FocusedNode_PreLollipop) {
            AccessibilityNodeInfo aFocusedNode_PreLollipop = mFocusedNode_PreLollipop;
            if (aFocusedNode_PreLollipop != null) {
                aFocusedNode_PreLollipop.recycle();
                mFocusedNode_PreLollipop = null;
            }
        }
    }

    private void clearFocusedNode_PreLollipop_INTRANSACTION() {
        //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) return;
        //need to keep that anyway if we want to find focus in WebViews

        synchronized (mSEMAPHORE_FocusedNode_PreLollipop) {

            mFocusedNode_PreLollipop_INTRANSACTION = null;
        }
    }

    private void findFocusedNodePreLollipop_startTransaction() {
        clearFocusedNode_PreLollipop_INTRANSACTION();
    }

    private void findFocusedNodePreLollipop_commitTransaction() {
        synchronized (mSEMAPHORE_FocusedNode_PreLollipop) {

            clearFocusedNode_PreLollipop();
            mFocusedNode_PreLollipop = mFocusedNode_PreLollipop_INTRANSACTION;

        }
    }


    private void checkFocusedNode_PreLollipop(AccessibilityNodeInfo node) {
        //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) return;
        //need to keep that anyway if we want to find focus in WebViews
        synchronized (mSEMAPHORE_FocusedNode_PreLollipop) {

            if (node.isFocused()) {
                mFocusedNode_PreLollipop_INTRANSACTION = AccessibilityNodeInfo.obtain(node);
            }
        }
    }

    private AccessibilityNodeInfo findFocus_PreLollipop() {
        //if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
        //    return null;
        //need to keep that anyway if we want to find focus in WebViews
        synchronized (mSEMAPHORE_FocusedNode_PreLollipop) {
            return mFocusedNode_PreLollipop == null ? null : AccessibilityNodeInfo.obtain(mFocusedNode_PreLollipop);
        }
    }

    private void publishChanges_MAIN() {
        checkHandlerThread();


        if (!mTree.isEmpty()) {
            if (LoggingConfig.INSTANCE.getLOG()) {
                Ln.d("DUMP: ------------------- TREE ------------------------");
                mTree.dump();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

                    AccessibilityNodeInfo focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);

                    Ln.d("DUMP: Focussed node: %s", focusedNode);
                    if (focusedNode != null) focusedNode.recycle();
                }
            }


            String aPackageName = mTree.getRoot().getPackageName().toString();
            boolean ours = mCore.getDb().isShowDecryptOverlay(aPackageName);
            if (!ours) {

                mCore.onAcsScrapeCompleted(aPackageName, null);
                return;
            }


            //TODO: opt we might want to recycle the nodes in the display tree,
            //however they do get processes async so not really shure how to do this....
            Tree.TreeNode displayTree = mTree.obtainDisplayTree();

            if (displayTree.getKey() != mLastRootNodeKey) {
                mLastRootNodeKey = displayTree.getKey();
                mCore.getEncryptionCache().clear(EncryptionCache.CLEAR_REASON.TREE_ROOT_CHANGED, null);
            }


            if (LoggingConfig.INSTANCE.getLOG()) {
                Ln.d("DUMP: ------------------- DISPLAYTREE ------------------------");
                StringBuffer sb = new StringBuffer();
                displayTree.dump(sb, "");
                Tree.dumpTreeNodePool("publish changes");
            }


            mCore.onAcsScrapeCompleted(aPackageName, displayTree);


        } else {
            mCore.getEncryptionCache().clear(EncryptionCache.CLEAR_REASON.EMPTY_TREE, null);
        }
        if (LoggingConfig.INSTANCE.getLOG()) {
            dumpPoolSize();
        }
    }


    public synchronized void performActionOnFocusedNode(PerformFocusedNodeAction action) {
        AccessibilityNodeInfo focusedNode = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        }
        //In case we're dealing with a webView, findFocus won't work and just remove the top frame,
        //so check if the focused node makes sense and if not, fall back to findFocus_PreLollipop
        if (focusedNode == null || !mTree.isAEditText(focusedNode)) {
            focusedNode = findFocus_PreLollipop();
        }
        if (focusedNode == null) {
            action.performActionWhenNothingFocused();
        } else {
            action.performAction(focusedNode);
            focusedNode.recycle();
        }
    }

    public synchronized void performNodeAction(PerformNodeAction action) {
        sendScrapeAllMessage("performNodeAction", action);

    }

    public interface PerformNodeAction {

        void onNodeScanned(AccessibilityNodeInfo node);

        void onScrapeComplete();
    }


    public interface PerformFocusedNodeAction {

        void performAction(AccessibilityNodeInfo node);

        void performActionWhenNothingFocused();

    }

    private static void dumpPoolSize() {
        //dumpAccessibilityNodeInfoCache();
        // dumpAccessibilityEventCache();
        //dumpAccessibilityRecordCache();

    }

    private static void dumpAccessibilityNodeInfoCache() {
        try {
            Field fPool = AccessibilityNodeInfo.class.getDeclaredField("sPool");
            fPool.setAccessible(true);
            Object sPool = fPool.get(null);
            Class cSynchronizedPool = Class.forName("android.util.Pools$SimplePool");
            Field fPoolSize = cSynchronizedPool.getDeclaredField("mPoolSize");
            fPoolSize.setAccessible(true);
            int poolSize = fPoolSize.getInt(sPool);
            Ln.d("AccessibilityNodeInfo POOL: %s ", poolSize);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    private static void dumpAccessibilityEventCache() {
        try {
            Field fPool = AccessibilityEvent.class.getDeclaredField("sPool");
            fPool.setAccessible(true);
            Object sPool = fPool.get(null);
            Class cSynchronizedPool = Class.forName("android.util.Pools$SimplePool");
            Field fPoolSize = cSynchronizedPool.getDeclaredField("mPoolSize");
            fPoolSize.setAccessible(true);
            int poolSize = fPoolSize.getInt(sPool);
            Ln.d("AccessibilityEvent POOL: %s", poolSize);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

//    private static void dumpAccessibilityRecordCache() {
//        try {
//            Field fPool = AccessibilityRecord.class.getDeclaredField("sPool");
//            fPool.setAccessible(true);
//            Object sPool = fPool.get(null);
//            if (sPool!=null) {
//                Class cSynchronizedPool = Class.forName("android.util.Pools$SimplePool");
//                Field fPoolSize = cSynchronizedPool.getDeclaredField("mPoolSize");
//                fPoolSize.setAccessible(true);
//                int poolSize = fPoolSize.getInt(sPool);
//                Ln.d("AccessibilityRecord POOL: %s", poolSize);
//            }
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//    }


    private static String recordToString(AccessibilityRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(" [ ClassName: " + record.getClassName());
        builder.append("; \tText: " + record.getText());
        builder.append("; \tContentDescription: " + record.getContentDescription());
        builder.append("; \tItemCount: " + record.getItemCount());
        builder.append("; \tCurrentItemIndex: " + record.getCurrentItemIndex());
        builder.append("; \tIsEnabled: " + record.isEnabled());
        builder.append("; \tIsPassword: " + record.isPassword());
        builder.append("; \tIsChecked: " + record.isChecked());
        builder.append("; \tIsFullScreen: " + record.isFullScreen());
        builder.append("; \tScrollable: " + record.isScrollable());
        builder.append("; \tBeforeText: " + record.getBeforeText());
        builder.append("; \tFromIndex: " + record.getFromIndex());
        builder.append("; \tToIndex: " + record.getToIndex());
        builder.append("; \tScrollX: " + record.getScrollX());
        builder.append("; \tScrollY: " + record.getScrollY());
        builder.append("; \tAddedCount: " + record.getAddedCount());
        builder.append("; \tRemovedCount: " + record.getRemovedCount());
        builder.append("; \tParcelableData: " + record.getParcelableData());

        AccessibilityNodeInfo source = record.getSource();
        builder.append("; \n\tsource: " + (source == null ? "NULL" : source.hashCode()));
        builder.append(" ]");
        if (source != null) {
            source.recycle();
        }
        return builder.toString();
    }

    private static String eventToString(AccessibilityEvent ev) {
        StringBuilder builder = new StringBuilder();
        builder.append("EventType: ").append(AccessibilityEvent.eventTypeToString(ev.getEventType()));
        //builder.append("; EventTime: ").append(ev.getEventTime());
        builder.append("; PackageName: ").append(ev.getPackageName());
        builder.append("; ClassName: ").append(ev.getClassName());
        // builder.append("; MovementGranularity: ").append(ev.getMovementGranularity());
        builder.append("; Action: ").append(ev.getAction());
        builder.append("; ContentChangeTypes: ");//.append(ev.getContentChangeTypes());


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            int cct = 0;
            cct = ev.getContentChangeTypes();
            boolean hasCONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) == AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION);
            boolean hasCONTENT_CHANGE_TYPE_SUBTREE = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
            boolean hasCONTENT_CHANGE_TYPE_TEXT = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) == AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);
            boolean hasCONTENT_CHANGE_TYPE_UNDEFINED = ((cct & AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);


            if (hasCONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) {
                builder.append(" DESCRIPTION,");
            }
            if (hasCONTENT_CHANGE_TYPE_SUBTREE) {
                builder.append(" SUBTREE,");
            }
            if (hasCONTENT_CHANGE_TYPE_TEXT) {
                builder.append(" TEXT,");
            }
            if (hasCONTENT_CHANGE_TYPE_UNDEFINED) {
                builder.append(" UNDEFINED,");
            }

            builder.append("\n");
        } else {
            builder.append("[ContentChangeType Not available on SDK " + android.os.Build.VERSION.SDK_INT);
        }

        builder.append("  Record X:");
        builder.append(recordToString(ev));
        if (true) {
            builder.append("\n");
            //builder.append("; sourceWindowId: ").append(mSourceWindowId);
            //if (mSourceNode != null) {
            //    builder.append("; mSourceNodeId: ").append(mSourceNode.getSourceNodeId());
            //}
            for (int i = 0; i < ev.getRecordCount(); i++) {
                final AccessibilityRecord record = ev.getRecord(i);
                builder.append("  Record ");
                builder.append(i);
                builder.append(":");
                builder.append(recordToString(record));

                builder.append("\n");
            }
        } else {
            builder.append("; recordCount: ").append(ev.getRecordCount());
        }
        return builder.toString();
    }


    private void checkHandlerThread() {
        if (Core.CHECK_THREADS && Thread.currentThread() != mHandler.getLooper().getThread()) {
            throw new RuntimeException("ILLEGAL THREAD " + Thread.currentThread().getName());
        }
    }
}
