package io.oversec.one.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;


import io.oversec.one.*;
import io.oversec.one.acs.util.AndroidIntegration;
import io.oversec.one.crypto.AppsReceiver;
import io.oversec.one.crypto.LoggingConfig;
import io.oversec.one.crypto.Help;

import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.ui.WithHelp;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.iab.FullVersionListener;
import io.oversec.one.iab.IabUtil;
import roboguice.util.Ln;


import java.util.Observable;
import java.util.Observer;

public class HelpFragment extends Fragment implements WithHelp, AppsReceiver.IAppsReceiverListener, FullVersionListener {

    private static final String TAG = "HelpFragment";
    private ViewGroup vgAcs;
    private ViewGroup vgBoss;
    private ViewGroup mVgOkc;
    private ViewGroup mVgUpgrade;
    private TextView mTvOkcStatus;

    private ViewGroup vgAcsOk;

    private Observer mAccessibilityServiceObserver = new Observer() {
        @Override
        public void update(Observable observable, Object data) {
            adjustVisibilities();
            boolean accessibilitySettingsOn = AndroidIntegration
                    .isAccessibilitySettingsOnAndServiceRunning(getActivity());

            if (accessibilitySettingsOn && !mPrevAccessibilitySettingsOn) {
                mPrevAccessibilitySettingsOn = true;

                showPostAcsEnableActivity();
            }
        }

    };
    private IabUtil mIabUtil;
    private ValueAnimator mColorAnimation;

    private void showPostAcsEnableActivity() {
        PostAcsEnableActivity.show(getActivity());
    }

    private boolean mPrevAccessibilitySettingsOn;


    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {

        mIabUtil = IabUtil.getInstance(getActivity());
        mIabUtil.addListener(this);

        mPrevAccessibilitySettingsOn = AndroidIntegration
                .isAccessibilitySettingsOnAndServiceRunning(getActivity());

        View v = inflater.inflate(R.layout.fragment_main_help, container, false);

        ViewGroup vgDebugStuff = (ViewGroup) v.findViewById(R.id.vg_debugstuff);

        Button btLogON = (Button) v.findViewById(R.id.btn_enable_logging);
        btLogON.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                LoggingConfig.INSTANCE.setLog(true);
            }
        });

        Button btLogOFF = (Button) v.findViewById(R.id.btn_disable_logging);
        btLogOFF.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                LoggingConfig.INSTANCE.setLog(false);
            }
        });

        Button btConsumeAll = (Button) v.findViewById(R.id.btn_consume_all);
        btConsumeAll.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                IabUtil.getInstance(getActivity()).consumeAll(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adjustVisibilities();
                            }
                        });
                    }
                });
            }
        });

        vgDebugStuff.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);

        Button btBugReport = (Button) v.findViewById(R.id.btn_bugreport);
        btBugReport.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                CrashActivity.sendBugReport(container.getContext(), null);
            }
        });


        Button btRePanic = (Button) v.findViewById(R.id.btn_repanic);
        btRePanic.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                Core.getInstance(container.getContext()).disablePanicMode();
                adjustVisibilities();
            }
        });

        Button btReBossHelp = (Button) v.findViewById(R.id.btn_bosskey_moreinfo);
        btReBossHelp.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Help.INSTANCE.open(getActivity(), Help.ANCHOR.bossmode_active);
            }
        });

        vgBoss = (ViewGroup) v.findViewById(R.id.settings_bossactive);

        Button btHelp = (Button) v.findViewById(R.id.btn_help);
        btHelp.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Help.INSTANCE.open(getActivity());
            }
        });

        Button btShare = (Button) v.findViewById(R.id.btn_share);
        btShare.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                Share.share(getActivity());
            }
        });

        vgAcsOk = (ViewGroup) v.findViewById(R.id.settings_acsenabledok);
        vgAcs = (ViewGroup) v.findViewById(R.id.settings_acsnotenabled);

        final Button btEnableAcs = (Button) v.findViewById(R.id.btn_enable_acs);
        btEnableAcs.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                openAcsSettings(container.getContext(), false);
            }
        });

        int colorFrom = Color.WHITE;
        int colorTo = Color.BLACK;
        mColorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        mColorAnimation.setInterpolator(new DecelerateInterpolator());
        mColorAnimation.setDuration(1000); // milliseconds
        mColorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        mColorAnimation.setRepeatMode(ValueAnimator.REVERSE);
        mColorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                btEnableAcs.setTextColor((int) animator.getAnimatedValue());
            }

        });
        mColorAnimation.start();


        v.findViewById(R.id.btn_enable_acs_moreinfo).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Help.INSTANCE.open(getActivity(), Help.ANCHOR.main_help_acsconfig);
            }
        });

        mVgOkc = (ViewGroup) v.findViewById(R.id.settings_okc);
        mTvOkcStatus = (TextView) v.findViewById(R.id.okc_status);
        Button btInstallOkcFdroid = (Button) v.findViewById(R.id.btn_okc_fdroid);
        btInstallOkcFdroid.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                OpenKeychainConnector.Companion.getInstance(getActivity()).openInFdroid();
            }
        });
        Button btInstallOkcPlaystore = (Button) v.findViewById(R.id.btn_okc_playstore);
        btInstallOkcPlaystore.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                OpenKeychainConnector.Companion.getInstance(getActivity()).openInPlayStore();
            }
        });
        btInstallOkcPlaystore.setVisibility(OpenKeychainConnector.Companion.getInstance(getActivity()).isGooglePlayInstalled() ? View.VISIBLE : View.GONE);


        Button btOkcMoreInfo = (Button) v.findViewById(R.id.btn_okc_moreinfo);
        btOkcMoreInfo.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Help.INSTANCE.open(getActivity(), Help.ANCHOR.encparams_pgp);
            }
        });

        adjustOkc();

        mVgUpgrade = (ViewGroup) v.findViewById(R.id.settings_upgrade);
        Button btUpgradeNow = (Button) v.findViewById(R.id.btn_upgrade_now);
        btUpgradeNow.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                IabUtil.getInstance(getActivity()).showPurchaseActivity(getActivity());
            }
        });

        adjustVisibilities();

        Core.getInstance(getActivity()).getObservableCore().addObserver(mAccessibilityServiceObserver);

        AppsReceiver.Companion.addListener(this);

        if (!AndroidIntegration
                .isAccessibilitySettingsOnAndServiceRunning(getActivity())) {
            if (savedInstanceState == null) { //clean create
                if (!OnboardingActivity.hasShownOnce()) {
                    openAcsSettings(getActivity(), true);
                }
            }
        }

        return v;
    }

    public void openAcsSettings(final Context ctx, boolean forceShowOnboarding) {
        mPrevAccessibilitySettingsOn = AndroidIntegration
                .isAccessibilitySettingsOnAndServiceRunning(ctx);

        Intent intent = new Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS);

        PackageManager packageManager = getActivity().getPackageManager();
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(
                    intent,
                    0);
            if (!GotItPreferences.Companion.getPreferences(ctx).isTooltipConfirmed(getString(R.string.tooltipid_onboarding))
                    || forceShowOnboarding) {
                vgBoss.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        OnboardingActivity.show(ctx);
                    }
                }, 500);
            }
        } else {
            Ln.w("No Intent available to handle ACTION_ACCESSIBILITY_SETTINGS");
            ActionAccessibilitySettingsNotResolvableActivity.show(ctx);
        }
    }

    private void adjustOkc() {
        mVgOkc.setVisibility(View.VISIBLE);

        if (!Util.isFeatureEnctypePGP(getActivity())) {
            mVgOkc.setVisibility(View.GONE);
            return;
        }

        int okcVersion = OpenKeychainConnector.Companion.getInstance(getActivity()).getVersion();
        if (okcVersion >= OpenKeychainConnector.V_MIN) {
            mVgOkc.setVisibility(View.GONE);
        } else {

            if (okcVersion == -1) {
                mTvOkcStatus.setText(io.oversec.one.crypto.R.string.settings_okc_not_installed);
            } else {
                mTvOkcStatus.setText(getString(io.oversec.one.crypto.R.string.okc_installed_but_too_old, OpenKeychainConnector.Companion.getInstance(getActivity()).getVersionName()));
            }
        }
    }

    @Override
    public void onDestroyView() {
        mColorAnimation.end();
        AppsReceiver.Companion.removeListener(this);
        mIabUtil.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onAppChanged(Context ctx, String action, String packagename) {
        adjustOkc();
    }

    @Override
    public void onResume() {
        adjustVisibilities();

        super.onResume();
    }

    @Override
    public void onDestroy() {
        Core.getInstance(getActivity()).getObservableCore().deleteObserver(mAccessibilityServiceObserver);

        super.onDestroy();

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        adjustVisibilities();

        boolean accessibilitySettingsOn = AndroidIntegration
                .isAccessibilitySettingsOnAndServiceRunning(getActivity());
        if (accessibilitySettingsOn) {

            ((MainActivity) getActivity()).showFirstTimeAppsTab();

        }

    }

    protected void adjustVisibilities() {
        if (getActivity() == null) {
            return;
        }

        boolean accessibilitySettingsOn = AndroidIntegration
                .isAccessibilitySettingsOnAndServiceRunning(getActivity());

        vgAcs.setVisibility(accessibilitySettingsOn ? View.GONE : View.VISIBLE);
        vgAcsOk.setVisibility(!accessibilitySettingsOn ? View.GONE : View.VISIBLE);
        vgBoss.setVisibility(Core.getInstance(getActivity()).getDb().isBossMode() ? View.VISIBLE : View.GONE);
        if (IabUtil.getInstance(getActivity()).isIabAvailable()) {
            IabUtil.getInstance(getActivity()).checkFullVersion(new FullVersionListener() {
                @Override
                public void onFullVersion_MAIN_THREAD(final boolean isFullVersion) {
                    mVgUpgrade.post(new Runnable() {
                        @Override
                        public void run() {
                            mVgUpgrade.setVisibility(isFullVersion ? View.GONE : View.VISIBLE);
                        }
                    });
                }
            });
        } else {
            mVgUpgrade.setVisibility(View.GONE);
        }
    }


    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.main_help;
    }

    @Override
    public void onFullVersion_MAIN_THREAD(boolean isFullVersion) {
        Activity a = getActivity();
        if (a != null) {
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        adjustVisibilities();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }

    }
}