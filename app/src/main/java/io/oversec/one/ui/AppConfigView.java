package io.oversec.one.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.NestedScrollView;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;

import io.oversec.one.Core;
import io.oversec.one.Util;
import io.oversec.one.db.Db;
import io.oversec.one.db.IDecryptOverlayLayoutParamsChangedListener;
import io.oversec.one.iab.FullVersionListener;
import io.oversec.one.iab.IabUtil;
import io.oversec.one.ovl.SampleNodeTextView;

import io.oversec.one.R;

public class AppConfigView extends ViewPager {

    private static final int TAB_COUNT = 3;
    private ContextThemeWrapper mCtw;
    private ColorSeekBar mCsBG;
    private ColorSeekBar mCsFG;
    private String mPackageName;
    private Db mDb;
    private SampleNodeTextView mSampleNodeTextView;
    private ColorSeekBar mCsButton;
    private Activity mActivity;
    private Core mCore;
    private ViewGroup mVgUpgradeReminder;
    private IDecryptOverlayLayoutParamsChangedListener mFabLayoutListener;

    public AppConfigView(Context context) {
        super(context);
        setUp();
    }

    public AppConfigView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp();
    }

    private void setUp() {
    }

    public void init(Activity activity, Db db, String packageName, TabLayout tabLayout, CharSequence packageLabel) {
        mPackageName = packageName;
        mDb = db;
        mActivity = activity;


        setOffscreenPageLimit(TAB_COUNT);
        setAdapter(new TabAdapter());

        { //Hack to make the preferences list scrollable
            View nsStuff = findViewById(R.id.tweaks_tab__stuff);
            ListView xlv = (ListView) nsStuff.findViewById(android.R.id.list);
            ViewCompat.setNestedScrollingEnabled(xlv, true);
        }
        {
            MainSettingsFragment frag = (MainSettingsFragment) activity.getFragmentManager().findFragmentById(R.id.tweaks_tab__stuff);
            frag.setPackageName(packageName);
        }

        mCore = Core.getInstance(getContext());

        tabLayout.setupWithViewPager(this);

        mVgUpgradeReminder = (ViewGroup) findViewById(R.id.upgrade_reminder);


        Button btUpgrade = (Button) findViewById(R.id.btn_upgrade);
        btUpgrade.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IabUtil.getInstance(mActivity).showPurchaseActivity(mActivity, AppConfigActivity.RQ_UPGRADE);
            }
        });

        mCsButton = (ColorSeekBar) findViewById(R.id.colorSliderButton);
        mCsButton.setBarMargin(0);
        mCsButton.setShowAlphaBar(true);

        int fullColor = mDb.getButtonOverlayBgColor(
                mPackageName);
        mCsButton.setAlphaBarValue(255 - Color.alpha(fullColor));
        mCsButton.setColor(fullColor);


        mCsButton.setOnColorChangeListener(new ColorSeekBar.OnColorChangeListener() {
            @Override
            public void onColorChangeListener(int colorBarValue, int alphaValue, final int color) {
                mDb.setButtonOverlayBgColor(mPackageName, color);
            }
        });

        mCsBG = (ColorSeekBar) findViewById(R.id.colorSliderBG);

        mCsBG.setShowAlphaBar(true);
        mCsBG.setBarMargin(0);

        mCsFG = (ColorSeekBar) findViewById(R.id.colorSliderFG);
        mCsFG.setShowAlphaBar(true);
        mCsFG.setBarMargin(0);

        fullColor = mDb.getDecryptOverlayBgColor(
                mPackageName);
        mCsBG.setAlphaBarValue(255 - Color.alpha(fullColor));
        mCsBG.setColor(fullColor);

        fullColor = mDb.getDecryptOverlayTextColor(
                mPackageName);
        mCsFG.setAlphaBarValue(255 - Color.alpha(fullColor));
        mCsFG.setColor(fullColor);

        mCsBG.setOnColorChangeListener(new ColorSeekBar.OnColorChangeListener() {
            @Override
            public void onColorChangeListener(int colorBarValue, int alphaValue, int color) {
                mDb.setDecryptOverlayBgColor(mPackageName, color);
            }
        });

        mCsFG.setOnColorChangeListener(new ColorSeekBar.OnColorChangeListener() {
            @Override
            public void onColorChangeListener(int colorBarValue, int alphaValue, int color) {
                mDb.setDecryptOverlayTextColor(mPackageName, color);
            }
        });

        {
            ViewGroup appearance = (ViewGroup) findViewById(R.id.tweaks_list_appearance);

            if (getResources().getBoolean(R.bool.feature_expert_options)) {
                appearance.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_statusicon, R.string.controls_hint_showstatusicons) {
                                       @Override
                                       boolean getValue() {
                                           return mDb.isShowStatusIcon(mPackageName);
                                       }

                                       @Override
                                       void setValue(boolean b) {
                                           mDb.setShowStatusIcon(mPackageName, b);
                                       }
                                   }
                        , 0);
            }
        }

        {
            ViewGroup expert = (ViewGroup) findViewById(R.id.tweaks_list_expert);

            if (!getResources().getBoolean(R.bool.feature_expert_options)) {
                expert.setVisibility(GONE);
            } else {

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_infobutton, R.string.controls_hint_showinfobutton) {
                    @Override
                    boolean getValue() {
                        return mDb.isShowInfoButton(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setShowInfoButton(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_infoontap, R.string.controls_hint_infoontap) {
                    @Override
                    boolean getValue() {
                        return mDb.isShowInfoOnTap(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setShowInfoOnTap(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_infoonlongtap, R.string.controls_hint_infoonlongtap) {
                    @Override
                    boolean getValue() {
                        return mDb.isShowInfoOnLongTap(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setShowInfoOnLongTap(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_encryptbutton, R.string.controls_hint_showencryptbutton) {
                    @Override
                    boolean getValue() {
                        return mDb.isShowEncryptButton(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setShowEncryptButton(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_toggleencryptonlongtap, R.string.controls_hint_toggleencryptonlongtap) {
                    @Override
                    boolean getValue() {
                        return mDb.isToggleEncryptButtonOnLongTap(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setToggleEncryptButtonOnLongTap(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_showuserinteractiondialogsimmediately, R.string.controls_hint_showuserinteractiondialogsimmediately) {
                    @Override
                    boolean getValue() {
                        return mDb.isShowUserInteractionDialogsImmediately(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setShowUserInteractionDialogsImmediately(mPackageName, b);
                    }
                });


                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_notification, R.string.controls_hint_shownotification) {
                    @Override
                    boolean getValue() {
                        return mDb.isShowNotification(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setShowNotification(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_overlayaboveinput, R.string.controls_hint_overlayaboveinput) {
                    @Override
                    boolean getValue() {
                        return mDb.isOverlayAboveInput(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setOverlayAboveInput(mPackageName, b);
                    }
                });


                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_voverflow, R.string.controls_hint_voverflow) {
                    @Override
                    boolean getValue() {
                        return mDb.isVoverflow(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setVoverflow(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_newlines, R.string.controls_hint_newlines) {
                    @Override
                    boolean getValue() {
                        return mDb.isAppendNewLines(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setAppendNewLines(mPackageName, b);
                    }
                });


                expert.addView(new IntSpinnerPref(getContext(), R.string.controls_spinner_innerpadding, R.string.controls_hint_innerpadding,
                        new Integer[]{0, 8, 32, 128, 512}
                ) {
                    @Override
                    int getValue() {
                        return mDb.getMaxInnerPadding(mPackageName);
                    }

                    @Override
                    void setValue(int v) {
                        mDb.setMaxInnerPadding(mPackageName, v);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_storeencryptionparamsperpackageonly, R.string.controls_hint_storeencryptionparamsperpackageonly) {
                    @Override
                    boolean getValue() {
                        return mDb.isStoreEncryptionParamsPerPackageOnly(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setStoreEncryptionParamsPerPackageOnly(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_forceencryptionparams, R.string.controls_hint_forceencryptionparams) {
                    @Override
                    boolean getValue() {
                        return mDb.isForceEncryptionParams(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setForceEncryptionParams(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_hqscrape, R.string.controls_hint_hqscrape) {
                    @Override
                    boolean getValue() {
                        return mDb.isHqScrape(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setHqScrape(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_includenotimporantviews, R.string.controls_hint_includenotimporantviews) {
                    @Override
                    boolean getValue() {
                        return mDb.isIncludeNonImportantViews(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setIncludeNonImportantViews(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_spreadinvisibleencoding, R.string.controls_hint_spreadinvisibleencoding) {
                    @Override
                    boolean getValue() {
                        return mDb.isSpreadInvisibleEncoding(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setSpreadInvisibleEncoding(mPackageName, b);
                    }
                });

                expert.addView(new CheckboxPref(getContext(), R.string.controls_checkbox_dontshowdecryptionfailed, R.string.controls_hint_dontshowdecryptionfailed) {
                    @Override
                    boolean getValue() {
                        return mDb.isDontShowDecryptionFailed(mPackageName);
                    }

                    @Override
                    void setValue(boolean b) {
                        mDb.setDontShowDecryptionFailed(mPackageName, b);
                    }
                });
            }
        }


        mSampleNodeTextView = (SampleNodeTextView) findViewById(R.id.sample_text_view);
        mSampleNodeTextView.onDecryptOverlayLayoutParamsChanged(mPackageName);
        Core.getInstance(getContext()).addDecryptOverlayLayoutParamsChangedListenerMainThread(mSampleNodeTextView);


        final ImageButton aSampleFab = (ImageButton) findViewById(R.id.fab);
        mFabLayoutListener = new IDecryptOverlayLayoutParamsChangedListener() {
            @Override
            public void onDecryptOverlayLayoutParamsChanged(String packagename) {
                LayerDrawable ld = (LayerDrawable) aSampleFab.getBackground();
                Drawable front = ld.getDrawable(1);

                GradientDrawable aButtonFront = null;
                if (front instanceof ShapeDrawable) {
                    aButtonFront = (GradientDrawable) front;
                } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) && (front instanceof RippleDrawable)) {
                    RippleDrawable rd = (RippleDrawable) front;
                    aButtonFront = (GradientDrawable) rd.getDrawable(1);
                }
                if (aButtonFront != null) {
                    aButtonFront.setColor(mDb.getButtonOverlayBgColor(mPackageName));
                }
            }
        };

        mFabLayoutListener.onDecryptOverlayLayoutParamsChanged(mPackageName);
        Core.getInstance(getContext()).addDecryptOverlayLayoutParamsChangedListenerMainThread(mFabLayoutListener);


        SeekBar sbFontSize = (SeekBar) findViewById(R.id.seekbar_fontsize);
        sbFontSize.setMax(30);
        sbFontSize.setProgress(mDb.getDecryptOverlayTextSize(
                mPackageName));
        sbFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mDb.setDecryptOverlayTextSize(mPackageName,
                        progress);
            }
        });

        SeekBar sbCorners = (SeekBar) findViewById(R.id.seekbar_corners);
        sbCorners.setMax(20);
        sbCorners.setProgress(mDb.getDecryptOverlayCornerRadius(
                mPackageName));
        sbCorners.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mDb.setDecryptOverlayCornerRadius(mPackageName,
                        progress);
            }
        });

        SeekBar sbPaddingLeft = (SeekBar) findViewById(R.id.seekbar_padding_left);
        sbPaddingLeft.setMax(12);
        sbPaddingLeft.setProgress(mDb.getDecryptOverlayPaddingLeft(
                mPackageName));
        sbPaddingLeft.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mDb.setDecryptOverlayPaddingLeft(mPackageName,
                        progress);
            }
        });


        SeekBar sbPaddingTop = (SeekBar) findViewById(R.id.seekbar_padding_top);
        sbPaddingTop.setMax(12);
        sbPaddingTop.setProgress(mDb.getDecryptOverlayPaddingTop(
                mPackageName));
        sbPaddingTop.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mDb.setDecryptOverlayPaddingTop(mPackageName,
                        progress);
            }
        });

        updateVisibilities();

    }

    public void updateVisibilities() {
        setFullVersion(false);
        IabUtil.getInstance(getContext()).checkFullVersion(new FullVersionListener() {
            @Override
            public void onFullVersion_MAIN_THREAD(final boolean isFullVersion) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        setFullVersion(isFullVersion);

                    }
                });
            }
        });
    }

    private void setFullVersion(boolean isFullVersion) {
        mVgUpgradeReminder.setVisibility(isFullVersion ? View.GONE : View.VISIBLE);
        mCsButton.setEnabled(isFullVersion);
        mCsBG.setEnabled(isFullVersion);
        mCsFG.setEnabled(isFullVersion);
    }

    class TabAdapter extends PagerAdapter {

        public Object instantiateItem(ViewGroup collection, int position) {

            int resId = 0;
            if (getContext().getResources().getBoolean(R.bool.feature_expert_options)) {
                switch (position) {
                    case 0:
                        resId = R.id.tweaks_tab__stuff;
                        break;
                    case 1:
                        resId = R.id.tweaks_tab__colors;
                        break;
                    case 2:
                        resId = R.id.tweaks_tab__expert;
                        break;
                }
            } else {
                switch (position) {
                    case 0:
                        resId = R.id.tweaks_tab__stuff;
                        break;
                    case 1:
                        resId = R.id.tweaks_tab__colors;
                        break;
                }
            }

            return findViewById(resId);
        }

        @Override
        public int getCount() {
            if (getContext().getResources().getBoolean(R.bool.feature_expert_options)) {
                return TAB_COUNT;
            } else {
                return TAB_COUNT - 1;
            }
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            int resId = 0;
            if (getContext().getResources().getBoolean(R.bool.feature_expert_options)) {
                switch (position) {

                    case 0:
                        resId = R.string.tweaks_tab__stuff;
                        break;
                    case 1:
                        resId = R.string.tweaks_tab__colors;
                        break;
                    case 2:
                        resId = R.string.tweaks_tab__expert;
                        break;
                }
            } else {
                switch (position) {

                    case 0:
                        resId = R.string.tweaks_tab__stuff;
                        break;
                    case 1:
                        resId = R.string.tweaks_tab__colors;
                        break;
                }
            }
            return getContext().getString(resId);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            //nothing
        }
    }

    public void destroy() {
        Core.getInstance(getContext()).removeDecryptOverlayLayoutParamsChangedListenerMainThread(mSampleNodeTextView);
        Core.getInstance(getContext()).removeDecryptOverlayLayoutParamsChangedListenerMainThread(mFabLayoutListener);
    }
}
