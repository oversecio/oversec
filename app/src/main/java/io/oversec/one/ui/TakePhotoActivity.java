package io.oversec.one.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;

import io.oversec.one.crypto.TemporaryContentProvider;
import io.oversec.one.crypto.ui.util.Util;


public class TakePhotoActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final String EXTRA_PACKAGENAME = "packagename";
    private String mPackagename;
    private Uri mCameraUri;

    public static void show(Context ctx, String packagename) {
        // check Android 6 permission
        if (Util.INSTANCE.checkCameraAccess(ctx)) {
            Intent i = new Intent();
            i.putExtra(EXTRA_PACKAGENAME, packagename);
            i.setClass(ctx, TakePhotoActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            ctx.startActivity(i);
        } else {
            Util.INSTANCE.showToast(ctx, "No Camera Permission!");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPackagename = getIntent().getStringExtra(EXTRA_PACKAGENAME);
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            mCameraUri = TemporaryContentProvider.Companion.prepare(this, "image/jpeg", TemporaryContentProvider.TTL_5_MINUTES, TemporaryContentProvider.TAG_CAMERA_SHOT);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.setClipData(ClipData.newRawUri(null, mCameraUri));
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraUri);

            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        TemporaryContentProvider.Companion.deleteUri(mCameraUri);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                ImageEncryptActivity.show(this, mPackagename, mCameraUri);
                finish();
            } else {
                finish();
            }
        }
    }

    public static boolean canResolveIntents(Context ctx, String packagename) {
        //device need to be able to take photos
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(ctx.getPackageManager()) != null) {

            //needs to correspond to ImageEncryptActivity's way of passing on the stuff...
            Intent shareIntent = new Intent();
            //target package need to be able to handle send image intents
            shareIntent.setPackage(packagename);
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            if (shareIntent.resolveActivity(ctx.getPackageManager()) != null) {
                return true;
            }
        }
        return false;
    }
}


