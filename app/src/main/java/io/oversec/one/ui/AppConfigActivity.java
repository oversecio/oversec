package io.oversec.one.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.kobakei.ratethisapp.RateThisApp;

import io.oversec.one.*;
import io.oversec.one.crypto.Help;
import io.oversec.one.iab.IabUtil;

public class AppConfigActivity extends AppCompatActivity {

    private static final String EXTRA_PACKAGENAME = "packagename";
    private static final String EXTRA_SELECTED_TAB = "EXTRA_SELECTED_TAB";
    public static final int RQ_UPGRADE = 9700;


    private Core mCore;
    private String mPackageName;
    private AppConfigView mTv;
    private TabLayout mTitleIndicator;


    public static void showForResult(Activity ctx, int rq, String packagename) {
        Intent i = new Intent();
        i.setClass(ctx, AppConfigActivity.class);
        i.putExtra(EXTRA_PACKAGENAME, packagename);
        ctx.startActivityForResult(i, rq);

    }


    public static void show(Context ctx, String packagename, View source) {
        Intent i = new Intent();

        ActivityOptions opts = null;

        if (source != null) {
            opts = ActivityOptions.makeScaleUpAnimation(source, 0, 0, 0, 0);
        }

        i.setClass(ctx, AppConfigActivity.class);
        i.putExtra(EXTRA_PACKAGENAME, packagename);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        if (opts != null) {
            ctx.startActivity(i, opts.toBundle());
        } else {
            ctx.startActivity(i);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RQ_UPGRADE) {
            mTv.updateVisibilities();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (IabUtil.isGooglePlayInstalled(this)) {
            RateThisApp.onCreate(this);
            RateThisApp.showRateDialogIfNeeded(this);
        }

        setContentView(R.layout.activity_appconfig);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        mCore = Core.getInstance(this);

        mPackageName = getIntent().getStringExtra(EXTRA_PACKAGENAME);

        CharSequence packageLabel = Util.getPackageLabel(this, mPackageName);
        if (Util.isOversec(this)) {
            getSupportActionBar().setSubtitle(packageLabel);
        }


        mTitleIndicator = (TabLayout) findViewById(R.id.tabs);

        mTv = (AppConfigView) findViewById(R.id.tweaks);
        mTv.init(this, mCore.getDb(), mPackageName, mTitleIndicator, packageLabel);


        final ViewGroup vgIsHidden = (ViewGroup) findViewById(R.id.vg_istemphidden);
        vgIsHidden.setVisibility(mCore.isTemporaryHidden(mPackageName) ? View.VISIBLE : View.GONE);

        Button btUnhide = (Button) findViewById(R.id.btnUnHide);
        btUnhide.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            mCore.undoTemporaryHide(mPackageName);
                                            vgIsHidden.setVisibility(View.GONE);
                                        }
                                    }
        );

        Button btApphelp = (Button) findViewById(R.id.btnAppHelp);
        btApphelp.setText(getString(R.string.action_help_perapp, packageLabel));
        btApphelp.setOnClickListener(new View.OnClickListener() {
                                         @Override
                                         public void onClick(View v) {
                                             Help.INSTANCE.openForPackage(AppConfigActivity.this, mPackageName);
                                         }
                                     }
        );

        Button btBugReport = (Button) findViewById(R.id.btnBugReport);
        btBugReport.setText(Util.isOversec(this) ? getString(R.string.action_send_bugreport_perapp, packageLabel) : getString(R.string.action_send_bugreport));
        btBugReport.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View v) {
                                               CrashActivity.sendBugReport(AppConfigActivity.this, mPackageName);
                                           }
                                       }
        );

        if (savedInstanceState != null) {
            int pos = savedInstanceState.getInt(EXTRA_SELECTED_TAB);
            mTv.setCurrentItem(pos);
        }

        btApphelp.setVisibility(Util.isOversec(this) ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        int numIgnoredKeys = Core.getInstance(this).getNumIgnoredTexts();
        MenuItem item = menu.findItem(R.id.menu_clear_ignored_stuff);
        item.setVisible(numIgnoredKeys > 0);
        item.setTitle(getString(R.string.action_clear_ignored_keys, numIgnoredKeys));

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            setupExitTransition();
        } else if (id == R.id.help) {
            Help.ANCHOR a = null;
            switch (mTitleIndicator.getSelectedTabPosition()) {
                case 0:
                    a = Help.ANCHOR.appconfig_main;
                    break;
                case 1:
                    a = Help.ANCHOR.appconfig_appearance;
                    break;
                case 2:
                    a = Help.ANCHOR.appconfig_lab;
                    break;
            }
            Help.INSTANCE.open(this, a);

            return true;
        } else if (id == R.id.action_share_app) {
            Share.share(this);
        } else if (id == R.id.about) {
            AboutActivity.show(this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setupExitTransition();
    }

    private void setupExitTransition() {
        overridePendingTransition(0, R.anim.activity_out);
    }


    @Override
    protected void onDestroy() {
        mTv.destroy();
        super.onDestroy();
        ImeMemoryLeakWorkaroundDummyActivity.maybeShow(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //DO NOT CALL SUPER, otherwise it'l fuck up our states
        // super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_SELECTED_TAB, mTv.getCurrentItem());
    }
}
