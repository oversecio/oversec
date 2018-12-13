package io.oversec.one.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.kobakei.ratethisapp.RateThisApp;

import io.oversec.one.*;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.sym.ui.KeysFragment;
import io.oversec.one.crypto.ui.WithHelp;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.iab.IabUtil;
import roboguice.util.Ln;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String EXTRA_TAB = "tab";
    private static final String TAB_HELP = "help";
    private static final String TAB_APPS = "apps";
    private static final String TAB_KEYS = "keys";
    private static final String TAB_SETTINGS = "settings";
    private static final String TAB_PADDER = "padder";
    private static final String EXTRA_INITIALIZED = "EXTRA_INITIALIZED";
    private static final String EXTRA_CONFIRM_DIALERCODE_BROADCAST_WORKING = "EXTRA_CONFIRM_DIALERCODE_BROADCAST_WORKING";

    private TabLayout mTabLayout;
    private static MainActivity mInstance;


    public static void show(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, MainActivity.class);
        i.putExtra(EXTRA_TAB, TAB_HELP);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    public static void showHelp(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, MainActivity.class);
        i.putExtra(EXTRA_TAB, TAB_HELP);
        ctx.startActivity(i);
    }

    public static void showApps(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, MainActivity.class);
        i.putExtra(EXTRA_TAB, TAB_APPS);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(i);

    }

    public static void showKeys(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, MainActivity.class);
        i.putExtra(EXTRA_TAB, TAB_KEYS);
        ctx.startActivity(i);

    }

    public static void showSettings(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, MainActivity.class);
        i.putExtra(EXTRA_TAB, TAB_SETTINGS);
        ctx.startActivity(i);

    }

    public static void confirmDialerSecretCodeBroadcastWorking(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, MainActivity.class);
        i.putExtra(EXTRA_TAB, TAB_SETTINGS);
        i.putExtra(EXTRA_CONFIRM_DIALERCODE_BROADCAST_WORKING, true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(i);
    }

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ArrayList<String> mTabs;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private HelpFragment mHelpFragment;
    private Fragment mAppsFragment;
    private KeysFragment mKeysFragment;
    private MainSettingsFragment mSettingsFragment;
    private PadderFragment mPadderFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mInstance = this;

        mTabs = new ArrayList<>();
        mTabs.add(TAB_HELP);
        if (Util.isOversec(this)) {
            mTabs.add(TAB_APPS);
        }
        if (Util.isFeatureEnctypeSYM(this)) {
            mTabs.add(TAB_KEYS);
        }
        mTabs.add(TAB_SETTINGS);
        mTabs.add(TAB_PADDER);

        super.onCreate(savedInstanceState);

        if (IabUtil.isGooglePlayInstalled(this)) {
            // Monitor launch times and interval from installation
            RateThisApp.onCreate(this);
            if (OpenKeychainConnector.Companion.getInstance(this).isGooglePlayInstalled()) {
                RateThisApp.showRateDialogIfNeeded(this);
            }
        }
        setContentView(R.layout.activity_main);

        mHelpFragment = new HelpFragment();
        if (Util.isOversec(this)) {
            mAppsFragment = new AppsFragment();
        }

        if (Util.isFeatureEnctypeSYM(this)) {
            mKeysFragment = new KeysFragment();
        }
        mSettingsFragment = new MainSettingsFragment();
        Bundle args = new Bundle();
        args.putBoolean(MainSettingsFragment.EXTRA_MAIN, true);
        mSettingsFragment.setArguments(args);

        mPadderFragment = new PadderFragment();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mTabLayout = (TabLayout) findViewById(R.id.tabs);


        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mTabLayout.setupWithViewPager(mViewPager);

        checkIntent(getIntent());


    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkIntent(intent);
    }


    private void checkIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_TAB)) {
            mViewPager.setCurrentItem(mTabs.indexOf(intent.getStringExtra(EXTRA_TAB)));
        }
        if (intent.hasExtra(EXTRA_CONFIRM_DIALERCODE_BROADCAST_WORKING)) {
            mSettingsFragment.confirmDialerSecretCodeBroadcastWorking(this);
        }
    }

    public void showFirstTimeAppsTab() {
        if (!GotItPreferences.Companion.getPreferences(this).isTooltipConfirmed(getString(R.string.tooltipid_apps_fragment))) {
            mViewPager.setCurrentItem(mTabs.indexOf(TAB_APPS));
        }
    }

    public static void closeOnPanic() {
        if (mInstance != null) {
            try {
                mInstance.finish();
            } catch (Exception ex) {
                Ln.e(ex);
            }
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            String tab = mTabs.get(position);

            switch (tab) {
                case TAB_APPS:
                    return mAppsFragment;
                case TAB_HELP:
                    return mHelpFragment;
                case TAB_KEYS:
                    return mKeysFragment;
                case TAB_PADDER:
                    return mPadderFragment;
                case TAB_SETTINGS:
                    return mSettingsFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {

            String tab = mTabs.get(position);

            switch (tab) {
                case TAB_APPS:
                    return getString(R.string.main_tab_apps);
                case TAB_HELP:
                    return getString(R.string.main_tab_help);
                case TAB_KEYS:
                    return getString(R.string.main_tab_keys);
                case TAB_PADDER:
                    return getString(R.string.main_tab_padder);
                case TAB_SETTINGS:
                    return getString(R.string.main_tab_settings);
            }
            return null;

        }
    }

//    @Override
//    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
//        mKeysFragment.handleActivityResult(requestCode,resultCode,data);
//        mAppsFragment.handleActivityResult(requestCode,resultCode,data);
//        mHelpFragment.handleActivityResult(requestCode,resultCode,data);
//        mPadderFragment.handleActivityResult(requestCode,resultCode,data);
//        mSettingsFragment.handleActivityResult(requestCode,resultCode,data);
//    }

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
        if (id == R.id.help) {
            Fragment f = mSectionsPagerAdapter.getItem(mTabLayout.getSelectedTabPosition());
            Help.ANCHOR a = null;
            if (f instanceof WithHelp) {
                a = ((WithHelp) f).getHelpAnchor();
            }
            Help.INSTANCE.open(this, a);

            return true;
        } else if (id == R.id.about) {
            AboutActivity.show(this);
        } else if (id == R.id.menu_clear_ignored_stuff) {
            Core.getInstance(this).clearIgnoredTexts();
        } else if (id == R.id.action_share_app) {
            Share.share(this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        Ln.d("onDestroy");
        mInstance = null;
        super.onDestroy();
        ImeMemoryLeakWorkaroundDummyActivity.maybeShow(this);
    }

    @Override
    protected void onStop() {
        Ln.d("onStop");
        super.onStop();
    }
}
