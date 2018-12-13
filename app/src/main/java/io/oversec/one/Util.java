package io.oversec.one;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;

import io.oversec.one.ui.MainActivity;

import java.util.List;

public class Util {

    public static CharSequence getPackageLabel(Context ctx, String packageName) {
        PackageManager lPackageManager = ctx.getPackageManager();
        ApplicationInfo lApplicationInfo = null;
        try {
            lApplicationInfo = lPackageManager.getApplicationInfo(packageName,
                    0);
        } catch (final PackageManager.NameNotFoundException e) {
            return packageName;
        }
        return lPackageManager.getApplicationLabel(lApplicationInfo);
    }

    public static void enableLauncherIcon(Context context, boolean enable) {
        ComponentName componentName = new ComponentName(context.getPackageName(), "io.oversec.one.MainActivityLauncher");
        setComponentState(context, componentName, enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        if (!enable) {
            MainActivity.closeOnPanic();
        }
    }

    private static void setComponentState(Context context, ComponentName componentName, int componentState) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(componentName, componentState, PackageManager.DONT_KILL_APP);
    }

    public static boolean hasDialerIntentHandler(Context context) {
        //or "is this a phone

        Intent intent = new Intent(Intent.ACTION_DIAL);
        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
        return infos.size() > 0;
    }

    public static boolean isOversec(Context ctx) {
        try {
            return ctx.getResources().getString(R.string.feature_package).trim().length() == 0;
        } catch (Resources.NotFoundException ex) {
            return true;
        }
    }

    public static boolean isFeatureEnctypeSYM(Context ctx) {
        return ctx.getResources().getBoolean(R.bool.feature_enctype_sym);

    }

    public static boolean isFeatureEnctypePGP(Context ctx) {
        return ctx.getResources().getBoolean(R.bool.feature_enctype_pgp);

    }
}
