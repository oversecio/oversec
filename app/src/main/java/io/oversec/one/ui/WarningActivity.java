package io.oversec.one.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import io.oversec.one.R;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.util.GotItPreferences;

public class WarningActivity extends Activity {
    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_GOTITID = "EXTRA_GOTITID";
    private static final String EXTRA_MOREINFO_TEXT = "EXTRA_MOREINFO_TEXT";
    private static final String EXTRA_MOREINFO_URL = "EXTRA_MOREINFO_URL" ;
    private TextView mTvMsg;
    private Button mBtOk;
    private Button mBtNeutral;
    private Button mBtCancel;
    private String mGotItId;

    public static void showAppWithSeriousIssuesEnabled(Context ctx, String packagename) {
        try {
            String gotitid = ctx.getString(R.string.tooltipid_serious_issues) + packagename;
            if (gotitid!=null && GotItPreferences.Companion.getPreferences(ctx).isTooltipConfirmed(gotitid)) {
                return;
            }

            Intent i = new Intent();

            CharSequence appName = ctx.getPackageManager().getApplicationLabel(
                    ctx.getPackageManager().getApplicationInfo(packagename, PackageManager.GET_META_DATA));
            String msg = ctx.getString(R.string.warning_app_with_serious_issues_enabled,appName);
            i.putExtra(EXTRA_MSG, msg);

            i.putExtra(EXTRA_GOTITID, gotitid );

            i.putExtra(EXTRA_MOREINFO_TEXT, ctx.getString(R.string.action_show_onlinehelp) );
            i.putExtra(EXTRA_MOREINFO_URL,  Help.INSTANCE.getAnchorForPackageInfos(ctx, packagename) );

            i.setClass(ctx, WarningActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warning);

        mTvMsg = (TextView) findViewById(R.id.text);
        mBtOk = (Button) findViewById(R.id.buttonOK);
        mBtNeutral = (Button) findViewById(R.id.buttonNeutral);
        mBtCancel = (Button) findViewById(R.id.buttonCancel);

        mTvMsg.setText(getIntent().getStringExtra(EXTRA_MSG));

        mBtNeutral.setVisibility(View.GONE);

        mGotItId = getIntent().getStringExtra(EXTRA_GOTITID);
        mBtOk.setText(R.string.action_gotit);
        mBtOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGotItId!=null) {
                    GotItPreferences.Companion.getPreferences(WarningActivity.this).setTooltipConfirmed(mGotItId);
                }
                finish();
            }
        });

        mBtCancel.setText(getIntent().getStringExtra(EXTRA_MOREINFO_TEXT));
        mBtCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGotItId!=null) {
                    GotItPreferences.Companion.getPreferences(WarningActivity.this).setTooltipConfirmed(mGotItId);
                }
                Help.INSTANCE.open(WarningActivity.this,getIntent().getStringExtra(EXTRA_MOREINFO_URL));
                finish();
            }
        });

    }

}
