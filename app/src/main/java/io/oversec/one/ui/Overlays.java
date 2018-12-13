package io.oversec.one.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.WindowManager;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.Util;
import io.oversec.one.acs.DisplayNodeVisitor;
import io.oversec.one.acs.Tree;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.Issues;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;
import io.oversec.one.iab.IabUtil;
import io.oversec.one.ovl.*;
import io.oversec.one.util.WrappedWindowManager;
import roboguice.util.Ln;
import java.util.*;

public class Overlays implements IDecryptOverlayLayoutParamsChangedListener {
    private final WrappedWindowManager mWm;
    private final Core mCore;
    private final int mMinimalHeightToShowInfoButton;
    private final IabUtil mIabUtil;
    private final Set<String> mIgnoreActivitiesWhitelist = new HashSet<>();
    private final Set<String> mIgnoreActivitiesBlacklist = new HashSet<>();
    private final boolean mIsOversec;
    private String mAppsecPackageName = null;

    private OverlayDecryptView mCurOverlayDecryptView;
    private OverlayEditTextBorderView mCurOverlayEditTextBorderView;
    private OverlayButtonEncryptView mCurOverlayButtonEncryptView;
    private OverlayButtonDecryptView mCurOverlayButtonDecryptView;
    private OverlayButtonInfomodeView mCurOverlayButtonInfomodeView;
    private OverlayButtonConfigView mCurOverlayButtonConfigView;
    private OverlayButtonCameraView mCurOverlayButtonCameraView;
    private OverlayButtonComposeView mCurOverlayButtonComposeView;
    private OverlayButtonHideView mCurOverlayButtonHideView;
    private OverlayDialogUserInteractionRequired mCurOverlayDialogUserInteractionRequired;
    private OverlayDialogInsufficientPaddingView mCurOverlayDialogInsufficientPadding;
    private OverlayDialogCorruptedEncodingView mCurOverlayDialogCorruptedEncoding;
    private OverlayDialogUpgrade mCurOverlayDialogUpgrade;

    private OverlayButtonUpgradeView mCurOverlayButtonUpgradeView;

    private OverlayDialogToast mCurOverlayDialogToast;
    private OverlayDialogTooltip mCurOverlayDialogTooltip;

    private OverlayOutsideTouchView mCurOverlayOutsideTouchView;
    private String mCurPackageName;
    private NodeTextView mFocusedView;

    public Overlays(Core core, Context ctx) {
        mCore = core;
        mIabUtil = IabUtil.getInstance(ctx);
        mWm = WrappedWindowManager.get(ctx);
        mMinimalHeightToShowInfoButton = mCore.dipToPixels(OverlayInfoButtonView.HEIGHT_DP) / 2;

        mIsOversec = Util.isOversec(ctx);
        if (!mIsOversec) {
            mAppsecPackageName = ctx.getResources().getString(R.string.feature_package);
            String[] featureActivityIgnoreWhitelist = ctx.getResources().getStringArray(R.array.feature_activity_ignore_whitelist);
            mIgnoreActivitiesWhitelist.addAll(Arrays.asList(featureActivityIgnoreWhitelist));
            String[] featureActivityIgnoreBlacklist = ctx.getResources().getStringArray(R.array.feature_activity_ignore_blacklist);
            mIgnoreActivitiesBlacklist.addAll(Arrays.asList(featureActivityIgnoreBlacklist));
        }

        mCore.addDecryptOverlayLayoutParamsChangedListenerUiThread(this);
    }

    @Override
    public String toString() {
        return "Overlays{" +
                ", mCurOverlayButtonEncryptView=" + mCurOverlayButtonEncryptView +

                ", mCurOverlayButtonInfomodeView=" + mCurOverlayButtonInfomodeView +
                ", mCurOverlayButtonConfigView=" + mCurOverlayButtonConfigView +
                ", mCurOverlayButtonCameraView=" + mCurOverlayButtonCameraView +
                ", mCurOverlayButtonHideView=" + mCurOverlayButtonHideView +
                ", mCurOverlayDecryptView=" + mCurOverlayDecryptView +
                ", mCurOverlayEditTextBorderView=" + mCurOverlayEditTextBorderView +
                ", mCurOverlayOutsideTouchView=" + mCurOverlayOutsideTouchView +
                ", mCurOverlayDialoguserInteractionRequired=" + mCurOverlayDialogUserInteractionRequired +
                ", mCurOverlayDialogInsufficientPadding=" + mCurOverlayDialogInsufficientPadding +
                ", mCurOverlayDialogCorruptedEncoding=" + mCurOverlayDialogCorruptedEncoding +
                ", mCurOverlayButtonUpgradeView=" + mCurOverlayButtonUpgradeView +
                ", mCurOverlayDialogToast=" + mCurOverlayDialogToast +
                ", mCurOverlayDialogTooltip=" + mCurOverlayDialogTooltip +
                '}';
    }

    public boolean isDecryptOverlayVisible() {
        return mCurOverlayDecryptView != null && !mCurOverlayDecryptView.isHidden();
    }

    public boolean isDecryptOverlayAdded() {
        return mCurOverlayDecryptView != null;
    }

    public void onScrapeComplete(NodeTextView focusedView, String currentPackageName, CharSequence className) {
        Ln.d("VOO:  onScrapeComplete, pn=%s   cn=%s",currentPackageName,className);

        boolean hideActivitySpecific = false;
        if (!mIsOversec && mAppsecPackageName.equals(currentPackageName)) {
            if (mIgnoreActivitiesBlacklist.contains(className)) {
                hideActivitySpecific=true;
            }
            else {
                hideActivitySpecific = !mIgnoreActivitiesWhitelist.contains(className);
            }
        }

        setHidden(mCore.isTemporaryHidden_UI(currentPackageName) || hideActivitySpecific);

        mCore.checkUiThread();

        boolean hasVisibleNodes = hasVisibleEncryptedNodes(false);
        boolean hasVisibleNodesNotIncludingActiveInput = hasVisibleEncryptedNodes(true);

        mFocusedView = focusedView;


        if (mCurOverlayButtonEncryptView != null) {
            mCurOverlayButtonEncryptView.onScrapeComplete(focusedView, hasVisibleNodesNotIncludingActiveInput);

        }

        if (mCurOverlayButtonDecryptView != null) {
            mCurOverlayButtonDecryptView.onScrapeComplete(focusedView, hasVisibleNodesNotIncludingActiveInput);
        }

        if (mCurOverlayButtonInfomodeView != null) {
            mCurOverlayButtonInfomodeView.onScrapeComplete(focusedView, hasVisibleNodes);
        }

        if (mCurOverlayButtonConfigView != null) {
            mCurOverlayButtonConfigView.onScrapeComplete(focusedView, hasVisibleNodes);
        }

        if (mCurOverlayButtonUpgradeView != null) {
            mCurOverlayButtonUpgradeView.onScrapeComplete(focusedView, hasVisibleNodes);
        }

        if (mCurOverlayButtonCameraView != null) {
            mCurOverlayButtonCameraView.onScrapeComplete(focusedView, hasVisibleNodes);
        }

        if (mCurOverlayButtonComposeView != null) {
            mCurOverlayButtonComposeView.onScrapeComplete(focusedView, hasVisibleNodes);
        }

        if (mCurOverlayButtonHideView != null) {
            mCurOverlayButtonHideView.onScrapeComplete(focusedView, hasVisibleNodes);
        }

        if (mCurOverlayEditTextBorderView != null) {
            mCurOverlayEditTextBorderView.onScrapeComplete(focusedView);
        }

        if (mCurOverlayDialogToast != null) {
            mCurOverlayDialogToast.onScrapeComplete(focusedView);
        }

        if (mCurOverlayDialogTooltip != null) {
            mCurOverlayDialogTooltip.onScrapeComplete(focusedView);
        }

        if (mCurOverlayDialogUserInteractionRequired != null) {
            mCurOverlayDialogUserInteractionRequired.onScrapeComplete(focusedView);
        }

        if (mCurOverlayDialogInsufficientPadding != null) {
            mCurOverlayDialogInsufficientPadding.onScrapeComplete(focusedView);
        }

        if (mCurOverlayDialogCorruptedEncoding != null) {
            mCurOverlayDialogCorruptedEncoding.onScrapeComplete(focusedView);
        }

        updateInfoButtons_int(currentPackageName);

        mCore.showOrUpdateNotification_UI();
    }

    public NodeTextView refreshDecrypt(Tree.TreeNode displayRoot) {
        mCore.checkUiThread();

        if (mCurOverlayDecryptView != null) {
            return mCurOverlayDecryptView.refresh(displayRoot);
        }
        return null;
    }

    public void showDecrypt(String packagename) {
        mCore.checkUiThread();

        mCurPackageName = packagename;
        if (mCurOverlayDecryptView != null) {
            removeDecrypt();
        }
        if (mCurOverlayDecryptView == null) {


            mCurOverlayDecryptView = new OverlayDecryptView(mCore, packagename);
            addOverlayView(mCurOverlayDecryptView, mCurOverlayDecryptView.getMyLayoutParams());

            mCurOverlayEditTextBorderView = new OverlayEditTextBorderView(mCore, packagename);
            addOverlayView(mCurOverlayEditTextBorderView, mCurOverlayEditTextBorderView.getMyLayoutParams());


            if (mIabUtil.isShowUpgradeButton(mCore.getCtx())) { mCurOverlayButtonUpgradeView = new OverlayButtonUpgradeView(mCore, packagename);
                addOverlayView(mCurOverlayButtonUpgradeView, mCurOverlayButtonUpgradeView.getMyLayoutParams());
            }


            mCurOverlayButtonConfigView = new OverlayButtonConfigView(mCore, packagename);
            addOverlayView(mCurOverlayButtonConfigView, mCurOverlayButtonConfigView.getMyLayoutParams());
            mCurOverlayButtonConfigView.hideMaster(!mCore.getDb().isShowConfigButton(mCurPackageName));


            mCurOverlayButtonInfomodeView = new OverlayButtonInfomodeView(mCore, packagename);
            addOverlayView(mCurOverlayButtonInfomodeView, mCurOverlayButtonInfomodeView.getMyLayoutParams());
            mCurOverlayButtonInfomodeView.hideMaster(!mCore.getDb().isShowInfoButton(mCurPackageName));

            mCurOverlayButtonCameraView = new OverlayButtonCameraView(mCore, packagename);
            addOverlayView(mCurOverlayButtonCameraView, mCurOverlayButtonCameraView.getMyLayoutParams());
            mCurOverlayButtonCameraView.hideMaster(
                    !mCore.getDb().isShowCameraButton(mCurPackageName)
                            || !TakePhotoActivity.canResolveIntents(mCore.getCtx(), mCurPackageName)
            );

            mCurOverlayButtonComposeView = new OverlayButtonComposeView(mCore, packagename);
            addOverlayView(mCurOverlayButtonComposeView, mCurOverlayButtonComposeView.getMyLayoutParams());
            mCurOverlayButtonComposeView.hideMaster(
                    !mCore.getDb().isShowComposeButton(mCurPackageName));

            mCurOverlayButtonHideView = new OverlayButtonHideView(mCore, packagename);
            addOverlayView(mCurOverlayButtonHideView, mCurOverlayButtonHideView.getMyLayoutParams());
            mCurOverlayButtonHideView.hideMaster(!mCore.getDb().isShowHideButton(mCurPackageName));
            mCurOverlayButtonHideView.hideButton(mCore.isTemporaryHidden_UI(mCurPackageName));


            mCurOverlayButtonEncryptView = new OverlayButtonEncryptView(mCore, packagename);
            addOverlayView(mCurOverlayButtonEncryptView, mCurOverlayButtonEncryptView.getMyLayoutParams());
            mCurOverlayButtonEncryptView.hideMaster(!mCore.getDb().isShowEncryptButton(mCurPackageName));

            mCurOverlayButtonDecryptView = new OverlayButtonDecryptView(mCore, packagename);
            addOverlayView(mCurOverlayButtonDecryptView, mCurOverlayButtonDecryptView.getMyLayoutParams());
            mCurOverlayButtonDecryptView.hideMaster(!mCore.getDb().isShowDecryptButton(mCurPackageName) || !mCore.getDb().isShowEncryptButton(mCurPackageName));


            mCurOverlayOutsideTouchView = new OverlayOutsideTouchView(mCore, packagename);
            addOverlayView(mCurOverlayOutsideTouchView, mCurOverlayOutsideTouchView.getMyLayoutParams());


            mCurOverlayEditTextBorderView.hideMaster(mInfoMode);
        }

        setHidden(mCore.isTemporaryHidden_UI(packagename));

        if (Issues.INSTANCE.hasKnownIssues(packagename)  && mCore.getDb().isShowKnownIssuesTooltips()) {
            try {
                showTooltip(
                        null,
                        mCore.getCtx().getString(R.string.tooltip_app_issues, Help.INSTANCE.getApplicationName(mCore.getCtx(),packagename)),
                        mCore.getCtx().getString(R.string.tooltipid_issues) + packagename,
                        Help.INSTANCE.getAnchorForPackageInfos(mCore.getCtx(), packagename),
                        false,
                        mCore.getCtx().getString(R.string.action_show_onlinehelp),
                        true
                        );
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    private void addOverlayView(OverlayView overlayView, WindowManager.LayoutParams layoutParams) {
        mWm.addView(overlayView, layoutParams);
    }

    private void mangleHideButtonTooltip(boolean hidden) {
        if (mCore.getDb().isShowHideButton(mCurPackageName)) {
            if (hidden) {
                showTooltip(mCurOverlayButtonHideView, mCore.getCtx().getString(R.string.tooltip_hidebutton_hidden), mCore.getCtx().getString(R.string.tooltipid_buttonhide_hidden), Help.ANCHOR.button_hide_hidden, true);
            } else {
                showTooltip(mCurOverlayButtonHideView, mCore.getCtx().getString(R.string.tooltip_hidebutton_nothidden), mCore.getCtx().getString(R.string.tooltipid_buttonhide_visible), Help.ANCHOR.button_hide_visible, false);
            }
        }
    }

    public void removeDecrypt() {
        mCore.checkUiThread();

        if (mCurOverlayDecryptView != null) {

            if (mCurOverlayOutsideTouchView != null) {
                mWm.removeView(mCurOverlayOutsideTouchView);
            }

            if (mCurOverlayButtonEncryptView != null) {
                mWm.removeView(mCurOverlayButtonEncryptView);
            }
            if (mCurOverlayButtonDecryptView != null) {
                mWm.removeView(mCurOverlayButtonDecryptView);
            }
            if (mCurOverlayButtonInfomodeView != null) {
                mWm.removeView(mCurOverlayButtonInfomodeView);
            }
            if (mCurOverlayButtonCameraView != null) {
                mWm.removeView(mCurOverlayButtonCameraView);
            }
            if (mCurOverlayButtonComposeView != null) {
                mWm.removeView(mCurOverlayButtonComposeView);
            }
            if (mCurOverlayButtonConfigView != null) {
                mWm.removeView(mCurOverlayButtonConfigView);
            }
            if (mCurOverlayButtonUpgradeView != null) {
                mWm.removeView(mCurOverlayButtonUpgradeView);
            }
            if (mCurOverlayButtonHideView != null) {
                mWm.removeView(mCurOverlayButtonHideView);
            }

            if (mCurOverlayEditTextBorderView != null) {
                mWm.removeView(mCurOverlayEditTextBorderView);
                mCurOverlayEditTextBorderView.destroy();
            }
            if (mCurOverlayDecryptView != null) {
                mWm.removeView(mCurOverlayDecryptView);
                mCurOverlayDecryptView.destroy();
            }

            removeDialogOverlayUserInteractionRequired();
            removeDialogOverlayInsufficientPadding();
            removeDialogCorruptedEncoding();
            removeDialogOverlayToast();
            removeDialogOverlayTooltip();
            removeDialogOverlayUpgrade();

            mCurOverlayDecryptView = null;
            mCurOverlayButtonInfomodeView = null;
            mCurOverlayButtonCameraView = null;
            mCurOverlayButtonComposeView = null;

            mCurOverlayButtonConfigView = null;
            mCurOverlayButtonUpgradeView = null;

            mCurOverlayButtonHideView = null;

            mCurOverlayButtonEncryptView = null;
            mCurOverlayButtonDecryptView = null;
            mCurOverlayEditTextBorderView = null;
            mCurOverlayOutsideTouchView = null;
        }
    }

    boolean mInfoMode;

    public boolean isInfoMode() {
        return mInfoMode;
    }

    public void toggleInfoMode(boolean state) {
        mCore.checkUiThread();

        boolean prevState = mInfoMode;
        mInfoMode = state;

        if (mCurOverlayEditTextBorderView != null) {
            mCurOverlayEditTextBorderView.hideMaster(mInfoMode);
        }

        if (!prevState && mInfoMode) {
            //force main button to be unfocussed
            if (mCurOverlayButtonEncryptView != null) {
                mCurOverlayButtonEncryptView.onScrapeComplete(null, true);
            }
            if (mCurOverlayButtonDecryptView != null) {
                mCurOverlayButtonDecryptView.onScrapeComplete(null, true);
            }
        }
    }

    List<OverlayInfoButtonView> mInfoButtons = new ArrayList<>();

    List<Tree.TreeNode> mTreeNodesForInfoButtonMangling = new ArrayList<>();


    private void updateInfoButtons_int(String packagename) {
        mCore.checkUiThread();

        if (mInfoMode && mCurOverlayDecryptView != null && !mCurOverlayDecryptView.isHidden()) {


            mTreeNodesForInfoButtonMangling.clear();
            mCurOverlayDecryptView.visitNodeViews(mInfoButtonVisitor);


            //update existing views, remove unneeded on the fly
            for (int i = 0; i < mInfoButtons.size(); i++) {
                if (i > mTreeNodesForInfoButtonMangling.size() - 1) {
                    mWm.removeView(mInfoButtons.get(i));
                    mInfoButtons.remove(i);
                } else {
                    OverlayInfoButtonView ib = mInfoButtons.get(i);
                    Tree.TreeNode tn = mTreeNodesForInfoButtonMangling.get(i);
                    ib.updateNode(tn.getOrigTextString(), tn.getBoundsInScreen());
                    mWm.updateViewLayout(ib, ib.getLayoutParams());
                }
            }

            //add additional views
            int s = mInfoButtons.size();
            for (int i = s; i < mTreeNodesForInfoButtonMangling.size(); i++) {
                Tree.TreeNode tn = mTreeNodesForInfoButtonMangling.get(i);
                OverlayInfoButtonView ib = new OverlayInfoButtonView(mCore, tn.getOrigTextString(), tn.getBoundsInScreen(), packagename);
                mInfoButtons.add(ib);
                addOverlayView(ib, ib.getMyLayoutParams());
            }


            if (mCurOverlayDialogUserInteractionRequired != null) {
                mWm.removeView(mCurOverlayDialogUserInteractionRequired);
                mCurOverlayDialogUserInteractionRequired = null;
                // addOverlayView(mCurOverlayDialogOKCIRQ, mCurOverlayDialogOKCIRQ.getMyLayoutParams());
            }

            if (mCurOverlayDialogInsufficientPadding != null) {
                mWm.removeView(mCurOverlayDialogInsufficientPadding);
                mCurOverlayDialogInsufficientPadding = null;
            }

            if (mCurOverlayDialogCorruptedEncoding != null) {
                mWm.removeView(mCurOverlayDialogCorruptedEncoding);
                mCurOverlayDialogCorruptedEncoding = null;
            }

            if (mCurOverlayDialogToast != null) {
                mWm.removeView(mCurOverlayDialogToast);
                mCurOverlayDialogToast = null;
            }

            if (mCurOverlayDialogTooltip != null) {
                mWm.removeView(mCurOverlayDialogTooltip);
                mCurOverlayDialogTooltip = null;
            }

        } else {
            for (OverlayInfoButtonView ib : mInfoButtons) {
                mWm.removeView(ib);
            }
            mInfoButtons.clear();
        }
    }

    private DisplayNodeVisitor mInfoButtonVisitor = new DisplayNodeVisitor() {


        @Override
        public void visit(NodeTextView ntv) {

            if ((ntv.getTryDecryptResult() != null && ntv.getTryDecryptResult().isOversecNode() || ntv.isUserInteractionRequired())) {
                if (ntv.getVisibleHeight() > mMinimalHeightToShowInfoButton) {
                    mTreeNodesForInfoButtonMangling.add(ntv.getNode());
                }
            }

        }
    };

    public void onOrientationChanged(int orientation) {
        mCore.checkUiThread();

        if (mCurOverlayDecryptView != null) {
            mCurOverlayDecryptView.onOrientationChanged(orientation);
        }
        if (mCurOverlayButtonEncryptView != null) {
            mCurOverlayButtonEncryptView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonDecryptView != null) {
            mCurOverlayButtonDecryptView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonHideView != null) {
            mCurOverlayButtonHideView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonInfomodeView != null) {
            mCurOverlayButtonInfomodeView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonCameraView != null) {
            mCurOverlayButtonCameraView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonComposeView != null) {
            mCurOverlayButtonComposeView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonConfigView != null) {
            mCurOverlayButtonConfigView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayButtonUpgradeView != null) {
            mCurOverlayButtonUpgradeView.onOrientationChanged(orientation, true);
        }
        if (mCurOverlayDialogToast != null) {
            mCurOverlayDialogToast.onOrientationChanged(orientation, true);
        }

        if (mCurOverlayDialogTooltip != null) {
            mCurOverlayDialogTooltip.onOrientationChanged(orientation, true);
        }

        if (mCurOverlayDialogUserInteractionRequired != null) {
            mCurOverlayDialogUserInteractionRequired.onOrientationChanged(orientation, true);
        }

        if (mCurOverlayDialogInsufficientPadding != null) {
            mCurOverlayDialogInsufficientPadding.onOrientationChanged(orientation, true);
        }

        if (mCurOverlayDialogCorruptedEncoding != null) {
            mCurOverlayDialogCorruptedEncoding.onOrientationChanged(orientation, true);
        }
    }

    public void setHidden(boolean hidden) {
        mCore.checkUiThread();

        if (mCurOverlayDecryptView != null) {
            mCurOverlayDecryptView.hideMaster(hidden);
        }
        if (mCurOverlayEditTextBorderView != null) {
            mCurOverlayEditTextBorderView.hideMaster(hidden);
        }
        if (mCurOverlayButtonEncryptView != null) {
            mCurOverlayButtonEncryptView.hideMaster(hidden);
        }
        if (mCurOverlayButtonDecryptView != null) {
            if (hidden) {
                mCurOverlayButtonDecryptView.hideMaster(true);
            } else {
                mCurOverlayButtonDecryptView.hideMaster(!mCore.getDb().isShowDecryptButton(mCurPackageName) || !mCore.getDb().isShowEncryptButton(mCurPackageName));
            }
        }
        if (mCurOverlayOutsideTouchView != null) {
            mCurOverlayOutsideTouchView.hideMaster(hidden);
        }
        if (mCurOverlayButtonHideView != null) {
            mCurOverlayButtonHideView.hideButton(hidden);

        }
        if (mCurOverlayButtonConfigView != null) {
            if (hidden) {
                mCurOverlayButtonConfigView.hideMaster(true);
            } else {
                mCurOverlayButtonConfigView.hideMaster(!mCore.getDb().isShowConfigButton(mCurPackageName));
            }
        }
        if (mCurOverlayButtonUpgradeView != null) {
            mCurOverlayButtonUpgradeView.hideMaster(hidden);
        }
        if (mCurOverlayButtonInfomodeView != null) {
            if (hidden) {
                mCurOverlayButtonInfomodeView.hideMaster(true);
            } else {
                mCurOverlayButtonInfomodeView.hideMaster(!mCore.getDb().isShowInfoButton(mCurPackageName));
            }
        }

        if (mCurOverlayButtonCameraView != null) {
            if (hidden) {
                mCurOverlayButtonCameraView.hideMaster(true);
            } else {
                mCurOverlayButtonCameraView.hideMaster(!mCore.getDb().isShowCameraButton(mCurPackageName)
                        || !TakePhotoActivity.canResolveIntents(mCore.getCtx(), mCurPackageName)
                );
            }
        }

        if (mCurOverlayButtonComposeView != null) {
            if (hidden) {
                mCurOverlayButtonComposeView.hideMaster(true);
            } else {
                mCurOverlayButtonComposeView.hideMaster(!mCore.getDb().isShowComposeButton(mCurPackageName));
            }
        }

        if (hidden) {
            removeDialogOverlayToast();
            //  mCore.resetLastTryToDecryptThisAgainToGetActualPendingIntent_UI();
        }

        mangleHideButtonTooltip(hidden);
    }

    @Override
    public void onDecryptOverlayLayoutParamsChanged(String packagename) {
        mCore.checkUiThread();

        if (mCurOverlayButtonInfomodeView != null) {
            mCurOverlayButtonInfomodeView.hideMaster(!mCore.getDb().isShowInfoButton(packagename));
        }

        if (mCurOverlayButtonCameraView != null) {
            mCurOverlayButtonCameraView.hideMaster(!mCore.getDb().isShowCameraButton(mCurPackageName)
                    || !TakePhotoActivity.canResolveIntents(mCore.getCtx(), mCurPackageName)
            );
        }

        if (mCurOverlayButtonComposeView != null) {
            mCurOverlayButtonComposeView.hideMaster(!mCore.getDb().isShowComposeButton(mCurPackageName));
        }

        if (mCurOverlayButtonConfigView != null) {
            mCurOverlayButtonConfigView.hideMaster(!mCore.getDb().isShowInfoButton(packagename));
        }
        if (mCurOverlayButtonHideView != null) {
            mCurOverlayButtonHideView.hideMaster(!mCore.getDb().isShowHideButton(packagename));
        }
        if (mCurOverlayButtonDecryptView != null) {
            mCurOverlayButtonDecryptView.hideMaster(!mCore.getDb().isShowDecryptButton(packagename) || !mCore.getDb().isShowEncryptButton(mCurPackageName));

        }
        if (mCurOverlayButtonEncryptView != null) {
            mCurOverlayButtonEncryptView.hideMaster(!mCore.getDb().isShowEncryptButton(packagename));
        }

        if (mCurOverlayDecryptView != null) {
            mCurOverlayDecryptView.onDecryptOverlayLayoutParamsChanged(packagename);
        }

        if (mCurOverlayEditTextBorderView != null) {
            mCurOverlayEditTextBorderView.onDecryptOverlayLayoutParamsChanged(packagename);
        }
    }

    public boolean showDialogOverlayUserInteractionRequired(String encodedText) {
        mCore.checkUiThread();

        if (mInfoMode) {
            return false;
        }

        if (mCurOverlayDialogUserInteractionRequired != null) {
            return false;
        } else {
            mCurOverlayDialogUserInteractionRequired = new OverlayDialogUserInteractionRequired(mCore, mCurPackageName, encodedText);
            addOverlayView(mCurOverlayDialogUserInteractionRequired, mCurOverlayDialogUserInteractionRequired.getMyLayoutParams());
            mCurOverlayDialogUserInteractionRequired.updatePosition();
            return true;
        }
    }

    public boolean showDialogUpgrade() {
        mCore.checkUiThread();

        if (mCurOverlayDialogUpgrade != null) {
            return false;
        } else {
            mCurOverlayDialogUpgrade = new OverlayDialogUpgrade(mCore);
            addOverlayView(mCurOverlayDialogUpgrade, mCurOverlayDialogUpgrade.getMyLayoutParams());
            mCurOverlayDialogUpgrade.updatePosition();
            return true;
        }
    }

    public void removeDialogOverlayUpgrade() {
        mCore.checkUiThread();

        if (mCurOverlayDialogUpgrade != null) {
            mWm.removeView(mCurOverlayDialogUpgrade);
            mCurOverlayDialogUpgrade = null;
        }
    }

    public void removeDialogOverlayUserInteractionRequired() {
        mCore.checkUiThread();

        if (mCurOverlayDialogUserInteractionRequired != null) {
            mWm.removeView(mCurOverlayDialogUserInteractionRequired);
            mCurOverlayDialogUserInteractionRequired = null;
        }
    }

    public void removeDialogOverlayToast() {
        mCore.checkUiThread();

        if (mCurOverlayDialogToast != null) {
            mWm.removeView(mCurOverlayDialogToast);
            mCurOverlayDialogToast = null;
        }
    }

    public void removeDialogOverlayTooltip() {
        mCore.checkUiThread();
        if (mCurOverlayDialogTooltip != null) {
            mWm.removeView(mCurOverlayDialogTooltip);
            mCurOverlayDialogTooltip = null;
        }
    }

    public void showDialogInsufficientPadding() {
        mCore.checkUiThread();

        if (mInfoMode) {
            return;
        }
        if (mCurOverlayDialogInsufficientPadding != null) {

        } else {


            mCurOverlayDialogInsufficientPadding = new OverlayDialogInsufficientPaddingView(mCore, mCurPackageName, mFocusedView);
            addOverlayView(mCurOverlayDialogInsufficientPadding, mCurOverlayDialogInsufficientPadding.getMyLayoutParams());
        }
    }

    public void removeDialogOverlayInsufficientPadding() {
        mCore.checkUiThread();

        if (mCurOverlayDialogInsufficientPadding != null) {
            mWm.removeView(mCurOverlayDialogInsufficientPadding);
            mCurOverlayDialogInsufficientPadding = null;
        }
    }

    public void showDialogCorruptedEncoding() {
        mCore.checkUiThread();

        if (mInfoMode) {
            return;
        }
        if (mCurOverlayDialogCorruptedEncoding != null) {

        } else {
            mCurOverlayDialogCorruptedEncoding = new OverlayDialogCorruptedEncodingView(mCore, mCurPackageName, mFocusedView);
            addOverlayView(mCurOverlayDialogCorruptedEncoding, mCurOverlayDialogCorruptedEncoding.getMyLayoutParams());
        }
    }

    public void removeDialogCorruptedEncoding() {
        mCore.checkUiThread();

        if (mCurOverlayDialogCorruptedEncoding != null) {
            mWm.removeView(mCurOverlayDialogCorruptedEncoding);
            mCurOverlayDialogCorruptedEncoding = null;
        }
    }


    public boolean hasVisibleEncryptedNodes(boolean excludeFocusedEditableEditText) {
        mCore.checkUiThread();

        if (mCurOverlayDecryptView != null) {
            return mCurOverlayDecryptView.hasVisibleEncryptedNodes(excludeFocusedEditableEditText);
        } else {
            return false;
        }
    }

    public void showToast(String msg) {
        mCore.checkUiThread();

        if (mCurOverlayDialogToast != null) {
            removeDialogOverlayToast();
        }
        mCurOverlayDialogToast = new OverlayDialogToast(mCore, mCurPackageName, msg);
        addOverlayView(mCurOverlayDialogToast, mCurOverlayDialogToast.getMyLayoutParams());
    }

    public void showTooltip(View anchor, String msg, String gotItId, Help.ANCHOR helpAnchor, boolean showEvenIfDecryptOverlayIsHidden) {
        showTooltip(anchor,msg,gotItId,helpAnchor.name(),showEvenIfDecryptOverlayIsHidden);
    }

    public void showTooltip(
            View anchor,
            String msg,
            String gotItId,
            String helpAnchor,
            boolean showEvenIfDecryptOverlayIsHidden) {
        showTooltip(anchor,msg,gotItId,helpAnchor,showEvenIfDecryptOverlayIsHidden,null,false);
    }

    public void showTooltip(
            View anchor,
            String msg,
            String gotItId,
            String helpAnchor,
            boolean showEvenIfDecryptOverlayIsHidden,
            String cancelText,
            boolean gotItOnCancel) {
        mCore.checkUiThread();

        if (gotItId!=null && GotItPreferences.Companion.getPreferences(mCore.getCtx()).isTooltipConfirmed(gotItId)) {
            return;
        }
        if (mCurOverlayDialogTooltip != null) {
            return;
        }
        mCurOverlayDialogTooltip = new OverlayDialogTooltip(
                mCore,
                mCurPackageName,
                msg,
                gotItId,
                helpAnchor,
                anchor,
                showEvenIfDecryptOverlayIsHidden,
                cancelText,
                gotItOnCancel
        );
        addOverlayView(mCurOverlayDialogTooltip, mCurOverlayDialogTooltip.getMyLayoutParams());
    }


    public void updateButtonVisibilities() {
        onDecryptOverlayLayoutParamsChanged(mCurPackageName);
    }

    public View getConfigButtonView() {
        if (mCurOverlayButtonConfigView != null) {
            return mCurOverlayButtonConfigView.getButtonView();
        }
        return null;
    }


    public View getEncryptButtonView() {
        if (mCurOverlayButtonEncryptView != null) {
            return mCurOverlayButtonEncryptView.getButtonView();
        }
        return null;
    }

    public void onFocusedEditTextDecrypted(NodeTextView nodeTextView, BaseDecryptResult tdr, boolean uIRE) {
        boolean hasVisibleNodes = hasVisibleEncryptedNodes(true);
        if (mCurOverlayButtonEncryptView != null) {
            mCurOverlayButtonEncryptView.onScrapeComplete(nodeTextView, hasVisibleNodes);
        }
        if (mCurOverlayEditTextBorderView != null) {
            mCurOverlayButtonEncryptView.onScrapeComplete(nodeTextView, hasVisibleNodes);
        }
    }

    public OverlayDecryptView getOverlayDecrypt() {
        return mCurOverlayDecryptView;
    }

    public void tempHideEncryptButton() {
        if (mCurOverlayButtonEncryptView != null) {
            mCurOverlayButtonEncryptView.hideMaster(true);
            mCurOverlayButtonEncryptView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    OverlayButtonEncryptView aCurOverlayButtonEncryptView = mCurOverlayButtonEncryptView;
                    if (aCurOverlayButtonEncryptView!=null) {
                        aCurOverlayButtonEncryptView.hideMaster(false);
                    }
                }
            }, 1000);
        }
    }

    public void removeButtonUpgrade() {
        if (mCurOverlayButtonUpgradeView != null) {
            mWm.removeView(mCurOverlayButtonUpgradeView);
            mCurOverlayButtonUpgradeView = null;
        }
    }
}
