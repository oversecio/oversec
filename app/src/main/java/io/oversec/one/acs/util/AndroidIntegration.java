package io.oversec.one.acs.util;

import android.content.Context;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import io.oversec.one.Core;
import io.oversec.one.acs.OversecAccessibilityService_1;

public class AndroidIntegration {

    public static boolean isAccessibilitySettingsOnAndServiceRunning(Context ctx) {
        return isAccessibilitySettingsOn(ctx) && Core.getInstance(ctx).isAccessibilityServiceRunning();
    }

    private static boolean isAccessibilitySettingsOn(Context ctx) {
        int accessibilityEnabled = 0;
        final String service = ctx.getPackageName() + "/"
                + OversecAccessibilityService_1.class.getName();
        boolean accessibilityFound = false;
        try {
            accessibilityEnabled = Settings.Secure.getInt(ctx
                            .getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(ctx
                            .getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                String[] values = settingValue.split("\\:");
                for (String string : values) {

                    if (string.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return accessibilityFound;
    }

}
