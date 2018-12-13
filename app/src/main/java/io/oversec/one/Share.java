package io.oversec.one;

import android.content.Context;
import android.content.Intent;

import io.oversec.one.R;

public class Share {

    public static void share(Context ctx) {
        try {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.share_body));
            sendIntent.setType("text/plain");
            ctx.startActivity(Intent.createChooser(sendIntent, ctx.getString(R.string.share_chooser_title)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
