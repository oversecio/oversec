package io.oversec.one.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.view.View;
import android.widget.Button;

import android.widget.TextView;
import io.oversec.one.R;

import java.util.Observable;
import java.util.Observer;

import io.oversec.one.Core;
import io.oversec.one.acs.util.AndroidIntegration;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import roboguice.util.Ln;

public class OnboardingActivity extends Activity {

    private static boolean SHOWN;

    public static void show(Context ctx) {
            Intent i = new Intent();
            i.setClass(ctx, OnboardingActivity.class);
            ctx.startActivity(i);
    }

    private Observer mAccessibilityServiceObserver = new Observer() {
        @Override
        public void update(Observable observable, Object data) {
            boolean accessibilitySettingsOn = AndroidIntegration
                    .isAccessibilitySettingsOnAndServiceRunning(OnboardingActivity.this) ;
            if (accessibilitySettingsOn) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(io.oversec.one.R.layout.activity_onboarding);
        Button btGotit = (Button) findViewById(R.id.btn_gotit);
        btGotit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GotItPreferences.Companion.getPreferences(OnboardingActivity.this).setTooltipConfirmed(getString(R.string.tooltipid_onboarding));
                finish();

            }
        });

        Button btMoreInfo = (Button) findViewById(R.id.btn_moreinfo);
        btMoreInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Help.INSTANCE.open(OnboardingActivity.this, Help.ANCHOR.main_help_acsconfig);

            }
        });

        TextView tv = (TextView)findViewById(android.R.id.text1);
        tv.setText(Html.fromHtml(getString(R.string.onboarding_text)));

        Core.getInstance(this).getObservableCore().addObserver(mAccessibilityServiceObserver);

        SHOWN = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Ln.d("yeah, onResume "+ Settings.canDrawOverlays(this));
        }
    }

    @Override
    public void onDestroy() {
        Core.getInstance(this).getObservableCore().deleteObserver(mAccessibilityServiceObserver);

        super.onDestroy();

    }

    public static boolean hasShownOnce() {
        return SHOWN;
    }
}
