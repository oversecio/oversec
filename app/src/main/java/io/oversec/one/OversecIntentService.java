package io.oversec.one;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;

import android.support.v4.content.ContextCompat;

import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.ui.ActionAccessibilitySettingsNotResolvableActivity;
import io.oversec.one.ui.AppConfigActivity;
import io.oversec.one.ui.OnboardingActivity;
import roboguice.util.Ln;

public class OversecIntentService extends IntentService {

    private static final String ACTION_TEMP_HIDE = "ACTION_TEMP_HIDE";
    private static final String ACTION_TEMP_SHOW = "ACTION_TEMP_SHOW";
    private static final String ACTION_SHOW_CONFIG = "ACTION_SHOW_CONFIG";
    private static final String ACTION_SHOW_ACCESSIBILITY_SETTINGS = "ACTION_SHOW_ACCESSIBILITY_SETTINGS";
    private static final String ACTION_STOPBOSS = "ACTION_STOPBOSS";
    private static final String ACTION_INFO_ON = "ACTION_INFO_ON";
    private static final String ACTION_INFO_OFF = "ACTION_INFO_OFF";

    private static final String EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME";

    public OversecIntentService() {
        super("oversec_intent_service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_TEMP_HIDE.equals(action)) {
            Core.getInstance(this).doTemporaryHide(intent.getStringExtra(EXTRA_PACKAGE_NAME), Boolean.TRUE);
            Core.getInstance(this).closeNotificationDrawer();

        } else if (ACTION_TEMP_SHOW.equals(action)) {
            Core.getInstance(this).doTemporaryHide(intent.getStringExtra(EXTRA_PACKAGE_NAME), Boolean.FALSE);
            Core.getInstance(this).closeNotificationDrawer();

        } else if (ACTION_SHOW_CONFIG.equals(action)) {
            AppConfigActivity.show(this, intent.getStringExtra(EXTRA_PACKAGE_NAME), null);
        } else if (ACTION_STOPBOSS.equals(action)) {
            Core.getInstance(this).panic();

        } else if (ACTION_SHOW_ACCESSIBILITY_SETTINGS.equals(action)) {

            Intent i = new Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager packageManager = getPackageManager();
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(i);
            } else {
                Ln.w("No Intent available to handle ACTION_ACCESSIBILITY_SETTINGS");
                ActionAccessibilitySettingsNotResolvableActivity.showFromService(this);
                ;
            }


        } else if (ACTION_INFO_ON.equals(action)) {
            Core.getInstance(this).toggleInfoMode(true);
            Core.getInstance(this).closeNotificationDrawer();
        } else if (ACTION_INFO_OFF.equals(action)) {
            Core.getInstance(this).toggleInfoMode(false);
            Core.getInstance(this).closeNotificationDrawer();
        }
    }

    public static Notification buildNotification(Context ctx, String packagename, boolean decryptOverlayIsShowing, boolean infoMode, boolean temporaryHidden) {

        Intent mainIntent = new Intent(ctx, OversecIntentService.class);
        mainIntent.setAction(temporaryHidden ? ACTION_TEMP_SHOW : ACTION_SHOW_CONFIG);
        mainIntent.putExtra(EXTRA_PACKAGE_NAME, packagename);

        PendingIntent pendingMainIntent = PendingIntent.getService(ctx, 0,
                mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent tempHideIntent = new Intent(ctx, OversecIntentService.class);
        tempHideIntent.setAction(ACTION_TEMP_HIDE);
        tempHideIntent.putExtra(EXTRA_PACKAGE_NAME, packagename);
        PendingIntent pendingTempHideIntent = PendingIntent.getService(ctx, 0,
                tempHideIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent tempShowIntent = new Intent(ctx, OversecIntentService.class);
        tempShowIntent.setAction(ACTION_TEMP_SHOW);
        tempShowIntent.putExtra(EXTRA_PACKAGE_NAME, packagename);
        PendingIntent pendingTempShowIntent = PendingIntent.getService(ctx, 0,
                tempShowIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent infoOnIntent = new Intent(ctx, OversecIntentService.class);
        infoOnIntent.setAction(ACTION_INFO_ON);
        PendingIntent pendingInfoOnIntent = PendingIntent.getService(ctx, 0,
                infoOnIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent infoOffIntent = new Intent(ctx, OversecIntentService.class);
        infoOffIntent.setAction(ACTION_INFO_OFF);
        PendingIntent pendingInfoOffIntent = PendingIntent.getService(ctx, 0,
                infoOffIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(ctx, OversecIntentService.class);
        stopIntent.setAction(ACTION_STOPBOSS);
        PendingIntent pendingStopIntent = PendingIntent.getService(ctx, 0,
                stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx)
                .setSmallIcon(R.drawable.ic_shutup_black_24dp)
                //  .setLargeIcon(largeIcon)
                .setColor(ContextCompat.getColor(ctx, io.oversec.one.crypto.R.color.colorPrimary))
                .setContentTitle(ctx.getString(temporaryHidden ? R.string.notification_title__hidden : R.string.notification_title__active))
                .setContentText(ctx.getString(temporaryHidden ? R.string.notification_body__hidden : R.string.notification_body__active))
                .setContentIntent(pendingMainIntent)

                .addAction(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                                R.drawable.ic_cancel_black_24dp : R.drawable.ic_cancel_white_24dp,
                        ctx.getString(R.string.notification_action_boss),
                        pendingStopIntent);
        if (!temporaryHidden) {
            builder.addAction(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                            R.drawable.ic_not_interested_black_24dp : R.drawable.ic_not_interested_white_24dp,
                    ctx.getString(decryptOverlayIsShowing ? R.string.notification_action_hide
                            : R.string.notification_action_show),
                    decryptOverlayIsShowing ? pendingTempHideIntent : pendingTempShowIntent);
        }
        if (decryptOverlayIsShowing && !temporaryHidden) {
            builder.addAction(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                            R.drawable.ic_info_outline_black_24dp : R.drawable.ic_info_outline_white_24dp,
                    ctx.getString(infoMode ? R.string.notification_action_unexplore
                            : R.string.notification_action_explore),
                    infoMode ? pendingInfoOffIntent : pendingInfoOnIntent);

        }

        Notification n = builder.build();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            n.visibility = Notification.VISIBILITY_SECRET;
        }
        n.priority = Notification.PRIORITY_MIN; //TODO: maybe make configurable
        return n;
    }

    public static Notification buildAcsNotRunningNotification(Context ctx) {
        Intent mainIntent = new Intent(ctx, OversecIntentService.class);
        mainIntent.setAction(ACTION_SHOW_ACCESSIBILITY_SETTINGS);
        PendingIntent pendingMainIntent = PendingIntent.getService(ctx, 0,
                mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx)
                .setSmallIcon(R.drawable.ic_info_black_24dp)
                // .setLargeIcon(largeIcon)
                .setColor(ContextCompat.getColor(ctx, io.oversec.one.crypto.R.color.colorPrimary))
                .setContentTitle(ctx.getString(R.string.notification_acsnotrunning_title))
                .setContentText(ctx.getString(R.string.notification_acsnotrunning_body))
                .setContentIntent(pendingMainIntent);
        Notification n = builder.build();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            n.visibility = Notification.VISIBILITY_SECRET;
        }
        return n;
    }
}
