package io.oversec.one;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import io.oversec.one.common.MainPreferences;
import io.oversec.one.ui.MainActivity;

public class SecretCodeReceiver extends BroadcastReceiver {
    public SecretCodeReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
            String uri = intent.getDataString();
            if (uri==null) return; //should never happen but aparently does happen :-(
            String[] sep = uri.split("://");
            String code = sep[1];
            if (MainPreferences.INSTANCE.getLauncherSecretDialerCode(context).equals(code)) {

                if (!MainPreferences.INSTANCE.isDialerSecretCodeBroadcastConfirmedWorking(context)) {
                    MainPreferences.INSTANCE.setDialerSecretCodeBroadcastConfirmedWorking(context);

                    MainActivity.confirmDialerSecretCodeBroadcastWorking(context);


                } else {
                    Core.getInstance(context).disablePanicMode();
                    Toast.makeText(context, context.getString(R.string.toast_panicmode_disabled), Toast.LENGTH_LONG).show();
                    //MainActivity.show(context);
                }
            }
        }
    }
}
