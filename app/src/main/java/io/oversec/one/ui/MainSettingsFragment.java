package io.oversec.one.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.*;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.Util;
import io.oversec.one.common.MainPreferences;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.WithHelp;
import io.oversec.one.db.Db;

public class MainSettingsFragment extends PreferenceFragment implements WithHelp, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int MIN_SECRETCODE_LENGTH = 4;
    public static final String EXTRA_MAIN = "main";
    private String mPackageName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager manager = getPreferenceManager();
        manager.setSharedPreferencesName(MainPreferences.INSTANCE.getFILENAME());
    }

    private void build() {
        PreferenceScreen ps = getPreferenceScreen();
        if (ps != null) {
            ps.removeAll();
            ;
        }
        addPreferencesFromResource(R.xml.empty_preferences);

        Activity ctx = getActivity();

        Bundle args = getArguments();
        if (args != null && args.getBoolean(EXTRA_MAIN)) {
            //Fragment used in MainActivity


            if (Util.isOversec(ctx)) {
                //Oversec: show prefs only
                addPreferencesFromResource(R.xml.main_preferences);
            } else {
                //Appsec: show prefs and per-app-stuff

                mPackageName = (getResources().getString(R.string.feature_package));
                addPreferencesFromResource(R.xml.main_preferences);
                addDbPrefs();

            }

        } else {
            //fragment used in app config
            if (Util.isOversec(ctx)) {
                //Oversec: show per-app-stuff only
                addDbPrefs();

            } else {
                //Appsec: show prefs and per-app-stuff
                addPreferencesFromResource(R.xml.main_preferences);
                addDbPrefs();

            }
        }

        if (!Util.hasDialerIntentHandler(getActivity())) {
            Preference p = getPreferenceScreen().findPreference(getString(R.string.mainprefs_hidelauncheronpanic_key));
            if (p != null) {
                getPreferenceScreen().removePreference(p);
            }
            Preference p2 = getPreferenceScreen().findPreference(getString(R.string.mainprefs_launchersecretcode_key));
            if (p2 != null) {
                getPreferenceScreen().removePreference(p2);
            }
        }

        if (!getResources().getBoolean(R.bool.feature_expert_options)) {
            Preference p3 = getPreferenceScreen().findPreference(getString(R.string.mainprefs_relaxecache_key));
            if (p3 != null) {
                getPreferenceScreen().removePreference(
                        p3);
            }
        }
    }

    private void addDbPrefs() {
        final Db db = Core.getInstance(getActivity()).getDb();
        Context ctx = getActivity();

        if (Util.isOversec(ctx)) {


            getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                    ctx.getString(R.string.controls_checkbox_enable),
                    ctx.getString(R.string.controls_checkbox_enable_sub, Util.getPackageLabel(ctx, mPackageName))) {
                @Override
                protected boolean getValue() {
                    return db.isAppEnabled(mPackageName);
                }

                @Override
                protected void setValue(boolean b) {
                    db.setAppEnabled(mPackageName, b);
                }
            });
        }

        getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                ctx.getString(R.string.controls_checkbox_configbutton),
                ctx.getString(R.string.controls_hint_configbutton)) {
            @Override
            protected boolean getValue() {
                return db.isShowConfigButton(mPackageName);
            }

            @Override
            protected void setValue(boolean b) {
                db.setShowConfigButton(mPackageName, b);
            }
        });


        getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                ctx.getString(R.string.controls_checkbox_starthidden),
                ctx.getString(R.string.controls_checkbox_starthidden_sub, Util.getPackageLabel(ctx, mPackageName))) {
            @Override
            protected boolean getValue() {
                return db.isStartHidden(mPackageName);
            }

            @Override
            protected void setValue(boolean b) {
                db.setStartHidden(mPackageName, b);
            }
        });


        getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                ctx.getString(R.string.controls_checkbox_hidebutton),
                ctx.getString(R.string.controls_checkbox_hidebutton_sub)) {
            @Override
            protected boolean getValue() {
                return db.isShowHideButton(mPackageName);
            }

            @Override
            protected void setValue(boolean b) {
                db.setShowHideButton(mPackageName, b);
            }
        });


        if (getResources().getBoolean(R.bool.feature_option_composebutton)) {

            getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                    ctx.getString(R.string.controls_checkbox_composebutton),
                    ctx.getString(R.string.controls_checkbox_composebutton_sub)) {
                @Override
                protected boolean getValue() {
                    return db.isShowComposeButton(mPackageName);
                }

                @Override
                protected void setValue(boolean b) {
                    db.setShowComposeButton(mPackageName, b);
                }
            });
        }

        getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                ctx.getString(R.string.controls_checkbox_decryptbutton),
                ctx.getString(R.string.controls_checkbox_decryptbutton_sub)) {
            @Override
            protected boolean getValue() {
                return db.isShowDecryptButton(mPackageName);
            }

            @Override
            protected void setValue(boolean b) {
                db.setShowDecryptButton(mPackageName, b);
            }
        });

        if (ctx.getResources().getBoolean(R.bool.feature_takephoto)
                && TakePhotoActivity.canResolveIntents(ctx, mPackageName)) {
            getPreferenceScreen().addPreference(new DbCheckBoxPreference(ctx,
                    ctx.getString(R.string.controls_checkbox_camerabutton),
                    ctx.getString(R.string.controls_checkbox_camerabutton_sub)) {
                @Override
                protected boolean getValue() {
                    return db.isShowCameraButton(mPackageName);
                }

                @Override
                protected void setValue(boolean b) {
                    db.setShowCameraButton(mPackageName, b);
                }
            });
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        build();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);


    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.main_settings;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.mainprefs_launchersecretcode_key))) {
            showCurrentLauncherCode();
        } else if (key.equals(getString(R.string.mainprefs_hidelauncheronpanic_key))) {
            check(key);
        } else if (key.equals(getString(R.string.mainprefs_screenoffpanic_key))) {
            check(key);
        }

    }

    private void check(String key) {
        if (MainPreferences.INSTANCE.isHideLauncherOnPanic(getActivity())) {
            if (!MainPreferences.INSTANCE.isDialerSecretCodeBroadcastConfirmedWorking(getActivity())) {

                CheckBoxPreference cb = (CheckBoxPreference)
                        getPreferenceScreen().findPreference(getString(R.string.mainprefs_hidelauncheronpanic_key));
                cb.setChecked(false);
                new MaterialDialog.Builder(getActivity())
                        .title(R.string.mainprefs_hidelauncheronpanic_title)
                        .iconRes(io.oversec.one.crypto.R.drawable.ic_vpn_key_black_24dp)
                        .cancelable(true)
                        .content(R.string.secretdialer_code_needs_to_be_set_up)
                        .neutralText(io.oversec.one.crypto.R.string.common_ok)

                        .show();
                return;
            }
        }

        if ((key.equals(getString(R.string.mainprefs_hidelauncheronpanic_key))
                && MainPreferences.INSTANCE.isHideLauncherOnPanic(getActivity())

        )
                ||
                (key.equals(getString(R.string.mainprefs_screenoffpanic_key))
                        && MainPreferences.INSTANCE.isPanicOnScreenOff(getActivity())
                )
                ) {
            String code = MainPreferences.INSTANCE.getLauncherSecretDialerCode(getActivity());
            if (code.length() == 0) {
                //create an initial launcher code
                code = String.valueOf(Math.random() * 100000).substring(0, 5);
                MainPreferences.INSTANCE.setLauncherSecretDialerCode(getActivity(), code);
            }
            if (Util.hasDialerIntentHandler(getActivity())) {
                showCurrentLauncherCode();
            }
        }


    }

    private void showCurrentLauncherCode() {
        String code = MainPreferences.INSTANCE.getLauncherSecretDialerCode(getActivity());
        String msg = getActivity().getString(R.string.secretdialer_code, code);


        if (!MainPreferences.INSTANCE.isDialerSecretCodeBroadcastConfirmedWorking(getActivity())) {
            msg = msg + "\n\n" + getActivity().getString(R.string.secretdialer_code_initial_confirm);

            if (android.os.Build.MANUFACTURER.toLowerCase().contains("samsung")) {
                msg = msg + "\n\n" + getActivity().getString(R.string.secretdialer_code_initial_confirm_samsung);
            }


            new MaterialDialog.Builder(getActivity())
                    .title(R.string.mainprefs_launchersecretcode_title)
                    .iconRes(io.oversec.one.crypto.R.drawable.ic_vpn_key_black_24dp)
                    .cancelable(true)
                    .content(msg)
                    .positiveText(io.oversec.one.crypto.R.string.action_show_dialer)
                    .negativeText(io.oversec.one.crypto.R.string.common_cancel)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    })
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            Intent intent = new Intent(Intent.ACTION_DIAL);
                            startActivity(intent);
                        }
                    })
                    .show();

        } else {
            new MaterialDialog.Builder(getActivity())
                    .title(R.string.mainprefs_launchersecretcode_title)
                    .iconRes(io.oversec.one.crypto.R.drawable.ic_vpn_key_black_24dp)
                    .cancelable(true)
                    .content(msg)
                    .neutralText(io.oversec.one.crypto.R.string.common_ok)

                    .show();
        }
    }

    public void confirmDialerSecretCodeBroadcastWorking(Context ctx) {
        new MaterialDialog.Builder(ctx)
                .title(R.string.mainprefs_launchersecretcode_title)
                .iconRes(io.oversec.one.crypto.R.drawable.ic_vpn_key_black_24dp)
                .cancelable(true)
                .content(R.string.secretdialer_code_initial_confirmed)
                .neutralText(io.oversec.one.crypto.R.string.common_ok)
                .show();
    }

    public void setPackageName(final String packageName) {
        mPackageName = packageName;
        build();
    }
}
