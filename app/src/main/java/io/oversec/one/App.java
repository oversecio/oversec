package io.oversec.one;

import android.app.ActivityManager;
import android.content.IntentFilter;
import android.os.Process;
import android.support.multidex.MultiDexApplication;

import com.kobakei.ratethisapp.RateThisApp;

import io.oversec.one.crypto.AppsReceiver;
import io.oversec.one.crypto.LoggingConfig;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.iab.IabUtil;

public class App extends MultiDexApplication {
    @Override
    public void onCreate() {

        int pid = Process.myPid();
        ActivityManager manager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        String currentProcName = null;
        for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == pid) {
                currentProcName = processInfo.processName;
                break;
            }
        }

        if (currentProcName.endsWith("zxcvbn")) {
            super.onCreate();
            return;
        }

        CrashHandler.init(this);

        LoggingConfig.INSTANCE.init(BuildConfig.DEBUG);

        super.onCreate();

        if (IabUtil.isGooglePlayInstalled(this)) {
            RateThisApp.Config config = new RateThisApp.Config(7, 30);
            RateThisApp.init(config);
        }

        //need to register from code, registering from manifest is ignored
        IntentFilter packageChangeFilter = new IntentFilter();
        packageChangeFilter.addAction("android.intent.action.PACKAGE_ADDED");
        packageChangeFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        packageChangeFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        packageChangeFilter.addDataScheme("package");
        registerReceiver(new AppsReceiver(), packageChangeFilter);

        IabUtil.getInstance(this);
        Core.getInstance(this);
    }
}
