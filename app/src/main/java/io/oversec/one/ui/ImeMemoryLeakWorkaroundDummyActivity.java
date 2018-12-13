package io.oversec.one.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import roboguice.util.Ln;

public class ImeMemoryLeakWorkaroundDummyActivity extends AppCompatActivity {

    public static void maybeShow(Activity activity) {
        //see https://code.google.com/p/android/issues/detail?id=171190
        //see https://code.google.com/p/android/issues/detail?id=205171
        //TODO: suppress for Android >= N where that bug should finally be fixed!
        activity.startActivity(new Intent(activity, ImeMemoryLeakWorkaroundDummyActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Ln.d("onCreate");
        super.onCreate(savedInstanceState);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 100);
    }

    @Override
    protected void onDestroy() {
        Ln.d("onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Ln.d("onStop");
        super.onStop();
    }

    @Override
    protected void onStart() {
        Ln.d("onStart");
        super.onStop();
    }
}
