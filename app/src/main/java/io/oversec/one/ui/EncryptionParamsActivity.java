package io.oversec.one.ui;

import android.app.Activity;
import android.app.ActivityOptions;

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
import android.view.View;
import android.view.ViewGroup;


import io.oversec.one.Core;
import io.oversec.one.R;

import io.oversec.one.Share;
import io.oversec.one.Util;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.AbstractEncryptionParams;


import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.EncryptionMethod;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.sym.SymmetricCryptoHandler;
import io.oversec.one.iab.IabUtil;
import io.oversec.one.ui.encparams.SimpleSymmetricEncryptionParamsFragment;
import io.oversec.one.crypto.ui.AbstractEncryptionParamsFragment;
import io.oversec.one.crypto.ui.EncryptionParamsActivityContract;


import io.oversec.one.ui.encparams.SymmetricEncryptionParamsFragment;

import io.oversec.one.crypto.UserInteractionRequiredException;
import io.oversec.one.crypto.ui.EncryptionInfoActivity;
import io.oversec.one.crypto.ui.WithHelp;
import io.oversec.one.ui.encparams.GpgEncryptionParamsFragment;
import roboguice.util.Ln;

public class EncryptionParamsActivity extends AppCompatActivity implements EncryptionParamsActivityContract, ViewPager.OnPageChangeListener {

    private static final String EXTRA_IME_WAS_VISIBLE = "ime_was_visible";
    private static final String EXTRA_EDIT_NODE_ID = "edit_node_id";
    private static final String EXTRA_PACKAGENAME = "packagename";
    private static final String EXTRA_TAB = "EXTRA_TAB";
    private static final String EXTRA_STATE_GPG = "STATE_GPG";
    private static final String EXTRA_STATE_SYM = "STATE_SYM";
    private static final String EXTRA_STATE_SIMPLESYM = "STATE_SIMPLESYM";
    private static final String EXTRA_MODE = "EXTRA_MODE";
    private static final String MODE_DEFAULT = "DEFAULT";
    private static final String MODE_IMAGE = "IMAGE";
    private static final String MODE_CLIPBOARD = "CLIPBOARD";

    private Core mCore;

    private boolean mImeWasVisible;

    private String mPackagename;
    private int mEditNodeId;
    private TabLayout mTitleIndicator;
    private TabAdapter mTabAdapter;

    private GpgEncryptionParamsFragment mGpgEncryptionParamsFragment;
    private SymmetricEncryptionParamsFragment mSymEncryptionParamsFragment;
    private SimpleSymmetricEncryptionParamsFragment mSimpleSymEncryptionParamsFragment;
    private ViewPager mPager;


    public static void show(Context ctx, String packagename, final CharSequence editText, final int nodeId, final boolean imeWasVisible, View source) {
        Intent i = new Intent();
        i.setClass(ctx, EncryptionParamsActivity.class);
        i.putExtra(EXTRA_IME_WAS_VISIBLE, imeWasVisible);
        i.putExtra(EXTRA_EDIT_NODE_ID, nodeId);
        i.putExtra(EXTRA_MODE, MODE_DEFAULT);
        i.putExtra(EXTRA_PACKAGENAME, packagename);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
        );


        //this should be called with _UN_encrypted text, so check first and if text is encrypted, open EncryptionInfo instead
        try {
            String s = editText == null ? null : editText.toString();
            BaseDecryptResult aResult = Core.getInstance(ctx).getEncryptionHandler().decrypt(s, null);
            if (aResult != null) {
                EncryptionInfoActivity.Companion.show(ctx, packagename, editText.toString(), source);
            }
        } catch (final UserInteractionRequiredException e) {
            EncryptionInfoActivity.Companion.show(ctx, packagename, editText.toString(), source);
            return;
        }


        ActivityOptions opts = null;

        if (source != null) {
            opts = ActivityOptions.makeScaleUpAnimation(source, 0, 0, 0, 0);
        }

        if (opts != null) {
            ctx.startActivity(i, opts.toBundle());
        } else {
            ctx.startActivity(i);
        }

    }

    public static void showForResult_ImageEncrypt(Activity ctx, String packagename, int requestCode) {
        Intent i = new Intent();
        i.setClass(ctx, EncryptionParamsActivity.class);
        i.putExtra(EXTRA_PACKAGENAME, packagename);
        i.putExtra(EXTRA_MODE, MODE_IMAGE);
        i.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);


        ctx.startActivityForResult(i, requestCode);

    }

    public static void showForResult_ClipboardEncrypt(Activity ctx, String packagename, int requestCode) {
        Intent i = new Intent();
        i.setClass(ctx, EncryptionParamsActivity.class);
        i.putExtra(EXTRA_PACKAGENAME, packagename);
        i.putExtra(EXTRA_MODE, MODE_CLIPBOARD);
        i.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);


        ctx.startActivityForResult(i, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //make sure to clear any pending decrypt tasks in the background, as they might interfere with pending intents generated by the user in the UI thread of this activity
        CryptoHandlerFacade.Companion.getInstance(this).clearDecryptQueue();

        setContentView(R.layout.activity_encryption_params);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //   getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCore = Core.getInstance(this);


        mImeWasVisible = getIntent().getBooleanExtra(EXTRA_IME_WAS_VISIBLE, false);
        mEditNodeId = getIntent().getIntExtra(EXTRA_EDIT_NODE_ID, 0);
        mPackagename = getIntent().getStringExtra(EXTRA_PACKAGENAME);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        boolean isForTextEncryption = MODE_DEFAULT.equals(mode) || MODE_CLIPBOARD.equals(mode);
        if (Util.isFeatureEnctypePGP(this)) {
            mGpgEncryptionParamsFragment = GpgEncryptionParamsFragment.newInstance(mPackagename, isForTextEncryption, savedInstanceState == null ? null : savedInstanceState.getBundle(EXTRA_STATE_GPG));
        }
        if (Util.isFeatureEnctypeSYM(this)) {
            mSymEncryptionParamsFragment = SymmetricEncryptionParamsFragment.newInstance(mPackagename, isForTextEncryption, savedInstanceState == null ? null : savedInstanceState.getBundle(EXTRA_STATE_SYM));
        }
        mSimpleSymEncryptionParamsFragment = SimpleSymmetricEncryptionParamsFragment.newInstance(mPackagename, isForTextEncryption, savedInstanceState == null ? null : savedInstanceState.getBundle(EXTRA_STATE_SIMPLESYM));
        mPager = (ViewPager) findViewById(R.id.pager);
        mTabAdapter = new TabAdapter(getFragmentManager());
        mPager.setOffscreenPageLimit(mTabAdapter.getCount());
        mPager.setAdapter(mTabAdapter);


        mPager.addOnPageChangeListener(this);


        mTitleIndicator = (TabLayout) findViewById(R.id.tabs);
        mTitleIndicator.setupWithViewPager(mPager);


        if (mTabAdapter.getCount() == 1) {
            mTitleIndicator.setVisibility(View.GONE);
            mSimpleSymEncryptionParamsFragment.setToolTipVisible(false);
        }

        if (savedInstanceState != null) {
            mPager.setCurrentItem(savedInstanceState.getInt(EXTRA_TAB));
        } else {
            AbstractEncryptionParams params = mCore.getBestEncryptionParams(mPackagename);

            //use last method of user activity or scraped
            EncryptionMethod method = params == null ? null : params.getEncryptionMethod();

            //no user activity, nothing scraped, let's see if we persisted it in database
            if (method == null) {
                method = mCore.getDb().getLastEncryptionMethod(mPackagename);
            }

            int page = 0;
            //even not in database?
            if (method != null) {
                page = mTabAdapter.getPageByMethod(method);
            }
            if (page < 0) page = 0;
            mPager.setCurrentItem(page);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_encryption_params, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isOnPgpTab = mTabAdapter.getPageByMethod(EncryptionMethod.GPG) == mTitleIndicator.getSelectedTabPosition();

        menu.findItem(R.id.action_pgp_choose_own_key).setVisible(isOnPgpTab);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            setupExitTransition();
            restoreImeStateOnBackPressed();
            return true;
        } else if (id == R.id.action_share_app) {
            Share.share(this);
            return true;
        } else if (id == R.id.action_pgp_choose_own_key) {
            mGpgEncryptionParamsFragment.triggerSigningKeySelection(null);
            return true;
        } else if (id == R.id.help) {
            Fragment f = mTabAdapter.getItem(mTitleIndicator.getSelectedTabPosition());
            Help.ANCHOR a = null;
            if (f instanceof WithHelp) {
                a = ((WithHelp) f).getHelpAnchor();
            }
            Help.INSTANCE.open(this, a);

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setupExitTransition();
        restoreImeStateOnBackPressed();
    }

    private void setupExitTransition() {
        overridePendingTransition(0, R.anim.activity_out);
    }

    private void restoreImeStateOnBackPressed() {
        if (mImeWasVisible) {
            mCore.bringUpSkb(500); //delay a bit to make sure the edit text is visible and focused
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        invalidateOptionsMenu();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }


    @Override
    public void finishWithResultOk() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void doEncrypt(AbstractEncryptionParams encryptionParams, boolean addLink) {
        //this stuff is left null when encrypting images, so still need to put something meaningful for next text encryption
        if (encryptionParams.getCoderId() == null) {
            encryptionParams.setXcoderAndPadderIds(getXCoderId(EncryptionMethod.GPG, mPackagename),
                    getPadderId(EncryptionMethod.GPG, mPackagename));
        }

        mCore.getDb().setLastEncryptionMethod(mPackagename, encryptionParams.getEncryptionMethod());

        if (mEditNodeId != 0) {
            mCore.doEncryptAndSaveParams(encryptionParams, mEditNodeId, mImeWasVisible, addLink, mPackagename);
        } else {
            mCore.setLastSavedUserSelectedEncryptionParams(encryptionParams, mPackagename);
        }
    }

    @Override
    public String getXCoderId(EncryptionMethod method, String packageName) {
        switch (method) {
            case SIMPLESYM:
            case SYM:
                return mCore.getDb().getSymXcoder(packageName);
            case GPG:
                return mCore.getDb().getGpgXcoder(packageName);
            default:
                throw new IllegalArgumentException();
        }

    }

    @Override
    public String getPadderId(EncryptionMethod method, String packageName) {
        switch (method) {
            case SIMPLESYM:
            case SYM:
                return mCore.getDb().getSymPadder(packageName);
            case GPG:
                return mCore.getDb().getGpgPadder(packageName);
            default:
                throw new IllegalArgumentException();
        }

    }

    @Override
    public void setXcoderAndPadder(EncryptionMethod method, String packageName, String coderId, String padderId) {
        switch (method) {
            case SIMPLESYM:
            case SYM:
                mCore.getDb().setSymXcoder(packageName, coderId);
                mCore.getDb().setSymPadder(packageName, padderId);
                break;
            case GPG:
                mCore.getDb().setGpgXcoder(packageName, coderId);
                mCore.getDb().setGpgPadder(packageName, padderId);
                break;
            default:
                throw new IllegalArgumentException();
        }

    }


    class TabAdapter extends FragmentPagerAdapter {

        public TabAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            AbstractEncryptionParamsFragment frag0 = null;
            AbstractEncryptionParamsFragment frag1 = null;
            AbstractEncryptionParamsFragment frag2 = null;

            if (Util.isFeatureEnctypeSYM(EncryptionParamsActivity.this) && Util.isFeatureEnctypePGP(EncryptionParamsActivity.this)) {
                if (OpenKeychainConnector.Companion.getInstance(EncryptionParamsActivity.this).isInstalled()) {
                    frag0 = mGpgEncryptionParamsFragment;
                    frag1 = mSymEncryptionParamsFragment;
                    frag2 = mSimpleSymEncryptionParamsFragment;
                } else {
                    SymmetricCryptoHandler sch = (SymmetricCryptoHandler) CryptoHandlerFacade.Companion.getInstance(EncryptionParamsActivity.this).getCryptoHandler(EncryptionMethod.SYM);
                    if (sch.hasAnyKey()) {
                        frag0 = mSymEncryptionParamsFragment;
                        frag1 = mGpgEncryptionParamsFragment;
                        frag2 = mSimpleSymEncryptionParamsFragment;
                    } else {
                        frag0 = mSimpleSymEncryptionParamsFragment;
                        frag1 = mSymEncryptionParamsFragment;
                        frag2 = mGpgEncryptionParamsFragment;

                    }
                }
            } else if (Util.isFeatureEnctypeSYM(EncryptionParamsActivity.this) && !Util.isFeatureEnctypePGP(EncryptionParamsActivity.this)) {
                SymmetricCryptoHandler sch = (SymmetricCryptoHandler) CryptoHandlerFacade.Companion.getInstance(EncryptionParamsActivity.this).getCryptoHandler(EncryptionMethod.SYM);
                if (sch.hasAnyKey()) {
                    frag0 = mSymEncryptionParamsFragment;
                    frag1 = mSimpleSymEncryptionParamsFragment;
                } else {
                    frag0 = mSimpleSymEncryptionParamsFragment;
                    frag2 = mSymEncryptionParamsFragment;

                }
            } else if (!Util.isFeatureEnctypeSYM(EncryptionParamsActivity.this) && Util.isFeatureEnctypePGP(EncryptionParamsActivity.this)) {
                if (OpenKeychainConnector.Companion.getInstance(EncryptionParamsActivity.this).isInstalled()) {
                    frag0 = mGpgEncryptionParamsFragment;
                    frag1 = mSimpleSymEncryptionParamsFragment;
                } else {
                    frag0 = mSimpleSymEncryptionParamsFragment;
                    frag1 = mGpgEncryptionParamsFragment;
                }
            } else {
                frag0 = mSimpleSymEncryptionParamsFragment;
            }


            //wtf, but let's be sure
            if (position == 1 && frag1 == null) {
                frag1 = frag0;
            }
            if (position == 2 && frag2 == null) {
                frag2 = frag0;
            }
            switch (position) {
                case 0:
                    frag0.setToolTipPosition(15);
                    return frag0;
                case 1:
                    frag1.setToolTipPosition(50);
                    return frag1;
                case 2:
                    frag2.setToolTipPosition(85);
                    return frag2;
            }

            return null;
        }


        @Override
        public int getCount() {
            return 1 + //always have simple password
                    (Util.isFeatureEnctypePGP(EncryptionParamsActivity.this) ? 1 : 0) +
                    (Util.isFeatureEnctypeSYM(EncryptionParamsActivity.this) ? 1 : 0);

        }

        @Override
        public CharSequence getPageTitle(int position) {
            AbstractEncryptionParamsFragment f = (AbstractEncryptionParamsFragment) getItem(position);
            return f.getTabTitle(EncryptionParamsActivity.this);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            //nothing
        }

        public int getPageByMethod(EncryptionMethod method) {
            for (int i = 0; i < getCount(); i++) {
                AbstractEncryptionParamsFragment f = (AbstractEncryptionParamsFragment) getItem(i);
                if (f.getMethod() == method) {
                    return i;
                }
            }
            return -1;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //GPG fragment depends on "startIntentSenderForResult" which can not be called directly from fragment
        //this it's called on this activity thus we need to forward the result
        mGpgEncryptionParamsFragment.onActivityResult(requestCode, resultCode, data);

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //do not save fragments and whatever the fuck
        //super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_TAB, mPager.getCurrentItem());
        Bundle b = null;
        if (mGpgEncryptionParamsFragment != null) {
            b = new Bundle();
            mGpgEncryptionParamsFragment.saveState(b);
            outState.putBundle(EXTRA_STATE_GPG, b);
        }
        if (mSimpleSymEncryptionParamsFragment != null) {
            b = new Bundle();
            mSimpleSymEncryptionParamsFragment.saveState(b);
            outState.putBundle(EXTRA_STATE_SIMPLESYM, b);
        }
        if (mSymEncryptionParamsFragment != null) {
            b = new Bundle();
            mSymEncryptionParamsFragment.saveState(b);
            outState.putBundle(EXTRA_STATE_SYM, b);
        }

    }

    @Override
    protected void onDestroy() {
        Ln.d("onDestroy");
        super.onDestroy();
        //NOT - maybe  lead to foxus problems in base app!   ImeMemoryLeakWorkaroundDummyActivity.maybeShow(this);
    }
}


