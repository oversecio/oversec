package io.oversec.one.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.common.MainPreferences;
import io.oversec.one.crypto.AbstractEncryptionParams;


public class ComposeActivity extends AppCompatActivity {

    private static final String EXTRA_PACKAGENAME = "EXTRA_PACKAGENAME";
    private static final int RQ_PARAMS = 666;
    private static final String EXTRA_PREFILL = "EXTRA_PREFILL";
    private String mPackageName;
    private EditText mEditText;

    public static void show(Context ctx, String packagename, String prefill) {
        Intent i = new Intent();
        i.setClass(ctx, ComposeActivity.class);
        i.putExtra(EXTRA_PACKAGENAME, packagename);
        //something's wrong here, sometimes shows encrypted text - for now better not prefill, no time to investigate
//        if (prefill!=null) {
//            i.putExtra(EXTRA_PREFILL, prefill);
//        }
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        ctx.startActivity(i);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MainPreferences.INSTANCE.isAllowScreenshots(this)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }

        setContentView(R.layout.activity_compose);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        Spannable text = new SpannableString(getSupportActionBar().getTitle());
        text.setSpan(new ForegroundColorSpan(Color.WHITE), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        getSupportActionBar().setTitle(text);

        mPackageName = getIntent().getStringExtra(EXTRA_PACKAGENAME);

        mEditText = (EditText) findViewById(R.id.edittext);
        String prefill = getIntent().getStringExtra(EXTRA_PREFILL);
        if (prefill != null) {
            mEditText.setText(prefill);
        }

        ImageButton fab = (ImageButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doEncrypt();

            }
        });
        fab.getBackground().setColorFilter(Core.getInstance(this).getDb().getButtonOverlayBgColor(mPackageName), PorterDuff.Mode.SRC_ATOP);

        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                EncryptionParamsActivity.showForResult_ClipboardEncrypt(ComposeActivity.this, mPackageName, RQ_PARAMS);
                return true;
            }
        });


    }

    private void doEncrypt() {

        Core core = (Core) Core.Companion.getInstance();

        AbstractEncryptionParams ep = core.getLastSavedUserSelectedEncryptionParams(mPackageName);

        if (ep == null
                || !ep.isStillValid(this)
                || core.getDb().isForceEncryptionParams(mPackageName)) {

            EncryptionParamsActivity.showForResult_ClipboardEncrypt(this, mPackageName, RQ_PARAMS);
        } else {
            core.doEncryptAndPutToClipboard(mEditText.getText().toString(), mPackageName, null, null);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (RQ_PARAMS == requestCode) {
            if (RESULT_OK == resultCode) {
                doEncrypt();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            setupExitTransition();
            return true;
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
}
