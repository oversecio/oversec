package io.oversec.one.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TimeUtils;
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
import java.util.concurrent.TimeUnit;

public class ManageOverlayPermissionWarningActivity extends Activity {
    private static long LATER = 0;

    public static void show(Context ctx) {
            if (System.currentTimeMillis()<LATER) {
                return;
            }
            Intent i = new Intent();
            i.setClass(ctx, ManageOverlayPermissionWarningActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overlaypermissionwarning);
        Button btGo = (Button) findViewById(R.id.btn_go);
        btGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                i.setData(Uri.parse("package:"+getPackageName()));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);

            }
        });
        Button btLater = (Button) findViewById(R.id.btn_later);
        btLater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LATER = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
                finish();

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    finish();
                }
        }
    }
}
