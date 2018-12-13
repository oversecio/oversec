package io.oversec.one;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class CrashHandler {

    public static void init(final App app) {
        final Thread.UncaughtExceptionHandler defaultUeh = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {

                System.out.println("********************* CRASH ******************* ");
                ex.printStackTrace();

                String stackTrace = buildStackTrace(ex);

                if (!hasFlagFile(app.getApplicationContext())) {

                    AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

                    Intent intent = CrashActivity.buildIntent(app, thread.getName(), stackTrace);
                    PendingIntent pi = PendingIntent.getActivity(app, 0, intent, 0);

                    //TODO: maybe write some counter value to disk and prevent an endless loop ??

                    am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2000, pi);

                    try {
                        writeFlagFile(app.getApplicationContext());
                    } catch (Exception ex2) {
                    }

                    if (!BuildConfig.DEBUG) {
                        System.exit(0);
                    }
                } else {
                    //we have a flag file, i.e. the app crashed previously, so if it crashes again we just
                    //call the standard exception handler to prevent loops,
                    //but remove the flag file so next time it'll work again!
                    try {
                        removeFlagFile(app.getApplicationContext());
                    } catch (Exception ex2) {
                    }
                    defaultUeh.uncaughtException(thread, ex);
                }
            }
        });
    }

    public static void removeFlagFile(Context context) {
        //noinspection ResultOfMethodCallIgnored
        getFlagFile(context).delete();
    }

    private static void writeFlagFile(Context context) {
        try {
            //noinspection ResultOfMethodCallIgnored
            getFlagFile(context).createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getFlagFile(Context context) {
        return new File(context.getCacheDir(), ".crashed");
    }

    private static boolean hasFlagFile(Context context) {
        return getFlagFile(context).exists();
    }

    private static String buildStackTrace(Throwable ex) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        ex.printStackTrace(printWriter);
        return result.toString();
    }
}
