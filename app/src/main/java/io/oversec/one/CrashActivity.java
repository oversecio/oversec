package io.oversec.one;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import io.oversec.one.BuildConfig;
import io.oversec.one.R;

import io.oversec.one.crypto.gpg.OpenKeychainConnector;

public class CrashActivity extends AppCompatActivity {

    private static final String EXTRA_STACKTRACE = "stacktrace";
    private static final String EXTRA_THREADNAME = "threadname";
    private static final int RQ_SEND_EMAIL = 1;

    public static void show(App app, String name, String stackTrace) {
        app.startActivity(buildIntent(app, name, stackTrace));
    }

    public static Intent buildIntent(App app, String name, String stackTrace) {
        final Intent intent = new Intent(app, CrashActivity.class);
        intent.putExtra(EXTRA_STACKTRACE, stackTrace);
        intent.putExtra(EXTRA_THREADNAME, name);

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.removeFlagFile(this);
        setContentView(R.layout.activity_crash);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendEmail();

            }
        });


        TextView tvStacktrace = (TextView) findViewById(R.id.stacktrace);
        tvStacktrace.setText(getCrashReport(Core.getInstance(this).getCurrentPackageName()));

    }

    private void sendEmail() {
        String emailAddress = getString(R.string.crash_email);

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto", emailAddress, null));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject));
        emailIntent.putExtra(Intent.EXTRA_TEXT, getCrashReport(null));


        startActivityForResult(Intent.createChooser(emailIntent, getString(R.string.crash_report_via_title)), RQ_SEND_EMAIL);
    }


    private String getCrashReport(String packagename) {
        StringBuilder sb = new StringBuilder();
        sb.append(getCommonInfo(this, packagename));
        sb.append("Thread: ").append(getIntent().getStringExtra(EXTRA_THREADNAME)).append("\n");
        sb.append("Stacktrace:\n");
        sb.append(getIntent().getStringExtra(EXTRA_STACKTRACE)).append("\n");
        return sb.toString();
    }

    private static String getCommonInfo(Context ctx, String packagename) {
        StringBuilder sb = new StringBuilder();
        sb.append("Application: ").append(BuildConfig.APPLICATION_ID).append("\n");
        sb.append("Version: ").append(BuildConfig.VERSION_CODE).append("\n");
        sb.append("Build-Type: ").append(BuildConfig.BUILD_TYPE).append("\n");
        sb.append("Flavor: ").append(BuildConfig.FLAVOR).append("\n");
        sb.append("Android-Version: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Android-Build: ").append(Build.FINGERPRINT).append("\n");
        sb.append("Hardware: ").append(Build.BRAND).append("/").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL).append(" / ").append(Build.PRODUCT).append("\n");
        if (OpenKeychainConnector.Companion.getInstance(ctx).isInstalled()) {
            sb.append("Open-Keychain-Version: ").append(OpenKeychainConnector.Companion.getInstance(ctx).getVersion());
        } else {
            sb.append("Open-Keychain: NOT INSTALLED");
        }
        sb.append("\n");

        if (packagename != null) {
            sb.append("-------------------------");
            sb.append("Client-Package: ").append(packagename);
            sb.append("\n");
            try {
                PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(packagename, 0);
                sb.append("Client-Version: ").append(pInfo.versionCode);
                sb.append("\n");

                sb.append(Core.getInstance(ctx).getDb().dumpSettings(packagename));
                sb.append("\n");

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }
        sb.append("-------------------------");
        sb.append("\n");

        return sb.toString();
    }

    private static String getBugReport(Context ctx, String packagename) {
        StringBuilder sb = new StringBuilder();
        sb.append(getCommonInfo(ctx, packagename));

        sb.append(ctx.getString(R.string.bug_report_footer));
        return sb.toString();
    }

    public static void sendBugReport(Context ctx, String packagename) {
        String emailAddress = ctx.getString(R.string.bug_email);

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto", emailAddress, null));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.bug_report_subject));
        emailIntent.putExtra(Intent.EXTRA_TEXT, getBugReport(ctx, packagename));


        ctx.startActivity(Intent.createChooser(emailIntent, ctx.getString(R.string.bug_report_via_title)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RQ_SEND_EMAIL) {
            finish();
        }
    }
}
