package io.oversec.one.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.acs.util.AndroidIntegration;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import roboguice.util.Ln;

import java.util.Observable;
import java.util.Observer;

public class ActionAccessibilitySettingsNotResolvableActivity extends Activity {

    public static void show(Context ctx) {
            Intent i = new Intent();
            i.setClass(ctx, ActionAccessibilitySettingsNotResolvableActivity.class);
            ctx.startActivity(i);
    }

    public static void showFromService(Context ctx) {
        Intent i = new Intent();
        i.setClass(ctx, ActionAccessibilitySettingsNotResolvableActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actionaccessibilitysettingsnotresolvable);
        Button btOk = (Button) findViewById(R.id.btn_ok);
        btOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                finish();

                Intent intent =  new Intent(
                        Settings.ACTION_SETTINGS);

                PackageManager packageManager = getPackageManager();
                if (intent.resolveActivity(packageManager) != null) {
                    startActivityForResult(
                            intent,
                            0);

                } else {
                    Ln.w("No Intent available to handle ACTION_SETTINGS");
                }

            }
        });

        Button btMoreInfo = (Button) findViewById(R.id.btn_moreinfo);
        btMoreInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Help.INSTANCE.open(ActionAccessibilitySettingsNotResolvableActivity.this, Help.ANCHOR.main_help_accessibilitysettingsnotresolvable);

            }
        });
    }
}
