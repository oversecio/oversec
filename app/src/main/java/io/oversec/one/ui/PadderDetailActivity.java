package io.oversec.one.ui;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import io.oversec.one.common.CoreContract;
import io.oversec.one.R;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.encoding.pad.PadderContent;
import io.oversec.one.db.PadderDb;

public class PadderDetailActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "EXTRA_ID";
    private static final int RQ_UPGRADE = 9001;
    private PadderDb mDb;
    private PadderContent mPc;
    private EditText mEtName;
    private EditText mEtContent;
    private TextInputLayout mNameWrapper,mContentWrapper;

    public static void showForResult(Fragment frag, int requestCode, Long id) {
        Intent i = new Intent();
        i.setClass(frag.getActivity(), PadderDetailActivity.class);
        if (id!=null) {
            i.putExtra(EXTRA_ID, id);
        }
        frag.startActivityForResult(i,requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDb = PadderDb.getInstance(this);

        setContentView(R.layout.activity_padder_detail);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CoreContract.Companion.getInstance().doIfFullVersionOrShowPurchaseDialog(PadderDetailActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                save();
                            }
                        });

                    }
                }, RQ_UPGRADE);
            }
        });

        mEtName = (EditText)findViewById(R.id.et_name);
        mEtContent = (EditText)findViewById(R.id.et_content);

        mNameWrapper = (TextInputLayout)findViewById(R.id.et_name_wrapper);
        mContentWrapper = (TextInputLayout)findViewById(R.id.et_content_wrapper);

        long id = getIntent().getLongExtra(EXTRA_ID,0);
        if (id!=0) {
            mPc = mDb.get(id);
            mEtName.setText(mPc.getName());
            mEtContent.setText(mPc.getContent());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode== RQ_UPGRADE) {
            if (resultCode==RESULT_OK) {
                save();
            }
        }
    }

    private void save() {
        boolean hasError = false;
        String name = mEtName.getText().toString();
        String content = mEtContent.getText().toString();
        if (name.length()<5) {
            mNameWrapper.setError(getString(R.string.error_padder_name_too_short, 5));
            hasError = true;
        }
        if (content.length()<5) {
            mContentWrapper.setError(getString(R.string.error_padder_content_too_short, 5));
            hasError = true;
        }
        if (hasError) {
            return;
        }

        if (mPc==null) {
            PadderContent pc = new PadderContent(name,content);
            mDb.add(pc);
            setResult(RESULT_OK);
            finish();
        }
        else {
            mPc.setName(mEtName.getText().toString());
            mPc.setContent(mEtContent.getText().toString());
            mPc.setSort(mPc.getName());
            mDb.update(mPc);
            setResult(RESULT_OK);
            finish();
        }
    }

    private void delete() {
        mDb.delete(mPc.getKey());
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_padder_details, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.help) {
            Help.INSTANCE.open(this, Help.ANCHOR.main_padders);
            return true;
        } else if (id == R.id.action_delete) {
            CoreContract.Companion.getInstance().doIfFullVersionOrShowPurchaseDialog(PadderDetailActivity.this, new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                           delete();
                        }
                    });

                }
            }, RQ_UPGRADE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        menu.findItem(R.id.action_delete).setVisible(mPc!=null );
        return super.onPrepareOptionsMenu(menu);
    }
}
