package io.oversec.one.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.protobuf.ByteString;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.AbstractEncryptionParams;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.TemporaryContentProvider;
import io.oversec.one.crypto.UserInteractionRequiredException;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.images.ImagePreferences;
import io.oversec.one.crypto.images.xcoder.ContentNotFullyEmbeddedException;
import io.oversec.one.crypto.images.xcoder.ImageXCoder;
import io.oversec.one.crypto.images.xcoder.blackandwhite.BlackAndWhiteImageXCoder;
import io.oversec.one.crypto.proto.Inner;
import io.oversec.one.crypto.proto.Outer;
import io.oversec.one.crypto.ui.BaseActivity;
import io.oversec.one.crypto.ui.util.ImageInfo;
import io.oversec.one.crypto.ui.util.ImgUtil;
import io.oversec.one.crypto.ui.util.Util;
import roboguice.util.Ln;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class ImageEncryptActivity extends BaseActivity {

    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 43;

    private static final int RESAMPLE_QUALITY = 90;

    private static final int RQ_ENCRYPTION_PARAMS = 2;
    private static final int RQ_PENDING_INTENT = 3;


    private ImageXCoder mCoder;
    private String mPackageName;
    private String mActivityName;
    private Uri mImageUri;
    private boolean mFromCore;
    private static int mSampleSizeS = 16; //static: We keep the last successfull sample size, assuming that images do always have the same resolution

    public static void show(Activity ctx, String packagename, Uri uri) {
        Intent i = new Intent();
        i.putExtra(Util.INSTANCE.EXTRA_PACKAGE_NAME, packagename);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClass(ctx, ImageEncryptActivity.class);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCoder = new BlackAndWhiteImageXCoder(this);
        init(getIntent(), savedInstanceState == null);
        if (mPackageName == null) {
            //need to ask user first where to send it
            //get possible recipients for ACTION_SEND , but wrap the, so they will call us again, this time with EXTRA_PACKAGENAME ( and EXTRA_ACTIVITYNAME) set
            Intent srcIntent = new Intent(Intent.ACTION_SEND);
            srcIntent.setType("image/*");

            Intent callback = new Intent(getIntent());
            callback.setClassName(getPackageName(), this.getClass().getName());
            callback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Util.INSTANCE.share(this, srcIntent, callback, getString(R.string.intent_chooser_share_encrypted_image), true, OpenKeychainConnector.Companion.getInstance(this).allPackageNames(), false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Ln.d("new intent " + intent);
        init(intent, false);
    }

    private void init(Intent intent, boolean initial) {
        mImageUri = intent.getData();
        if (mImageUri == null) {
            mImageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (mImageUri == null) {
            //received a bad intent, nothing we can do about it...
            finish();
            return;
        }

        //try if we can still read it
        try {
            getContentResolver().openInputStream(mImageUri);
        } catch (FileNotFoundException e) {
            if (Util.INSTANCE.checkExternalStorageAccess(this, e)) {
                ActivityCompat.requestPermissions(ImageEncryptActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                showError(getString(R.string.error_image_encode_source_not_found), new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
            return;
        }


        mPackageName = intent.getStringExtra(Util.INSTANCE.EXTRA_PACKAGE_NAME);
        mActivityName = intent.getStringExtra(Util.INSTANCE.EXTRA_ACTIVITY_NAME);

        if (mPackageName != null) {
            if (mActivityName == null) {
                //we've been called from core
                mFromCore = true;
            } else {
                mFromCore = false;
            }

            if (!initial || mFromCore) { //only call this on new intent or when called through the camera button
                doEncode();
            }
        }
    }

    @Override
    public void onBackPressed() {
        //the data is not necessarily coming from the camera provider,
        //but let the provider decide if he can handle this
        TemporaryContentProvider.Companion.deleteUri(mImageUri);

        super.onBackPressed();
    }

    private void doEncode() {
        if (mImageUri != null) {
            ImagePreferences.Companion.getPreferences(this).setXCoder(mCoder.getClass().getSimpleName());
            //there is no real way to know which encryption params we should use, so ALWAYS let the user choose or confirm!
            EncryptionParamsActivity.showForResult_ImageEncrypt(this, mPackageName, RQ_ENCRYPTION_PARAMS);
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        //try if we can still read it
        try {
            getContentResolver().openInputStream(mImageUri);
        } catch (FileNotFoundException e) {
            if (Util.INSTANCE.checkExternalStorageAccess(this, e)) {
                ActivityCompat.requestPermissions(ImageEncryptActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                showError(getString(R.string.error_image_encode_source_not_found), new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
            return;
        }


        if (requestCode == RQ_ENCRYPTION_PARAMS) {
            if (resultCode == RESULT_OK) {

                try {
                    doWithEncryptionParamsSet(data);

                } catch (Exception e) {
                    e.printStackTrace();
                    Util.INSTANCE.showToast(this, getString(R.string.error_image_encode_generic));
                }
            } else {
                finish();
            }
        } else if (requestCode == RQ_PENDING_INTENT) {
            try {
                doWithEncryptionParamsSet(data);

            } catch (Exception e) {
                e.printStackTrace();
                Util.INSTANCE.showToast(this, getString(R.string.error_image_encode_generic));
            }
        }
    }

    private void doWithEncryptionParamsSet(final Intent encryptExtras) throws GeneralSecurityException, IOException {
        Dialog d = null;
        try {
            d = new MaterialDialog.Builder(this)
                    .title(getString(R.string.please_wait))
                    .content(getString(R.string.please_wait_encrypting))
                    .progress(true, 0)
                    .cancelable(false)
                    .show();
        } catch (Exception ex) {

        }
        final Dialog progressDialog = d;

        new Thread(new Runnable() {
            @Override
            public void run() {
                AbstractEncryptionParams params = Core.getInstance(ImageEncryptActivity.this).getLastSavedUserSelectedEncryptionParams(mPackageName);

                try {
                    final Uri uri = transcode(mImageUri, mCoder, params, encryptExtras);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if (uri != null) {
                                try {
                                    share(uri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                Util.INSTANCE.showToast(ImageEncryptActivity.this, getString(R.string.error_image_encode_generic));
                            }
                        }
                    });
                } catch (UserInteractionRequiredException e) {
                    try {
                        startIntentSenderForResult(e.getPendingIntent().getIntentSender(), RQ_PENDING_INTENT, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e1) {
                        e1.printStackTrace();
                    }
                } catch (ContentNotFullyEmbeddedException ce) {

                    //start over
                    mSampleSizeS = mSampleSizeS * 2;

                    try {
                        doWithEncryptionParamsSet(null);
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Util.INSTANCE.showToast(ImageEncryptActivity.this, getString(R.string.error_image_encode_generic));
                        }
                    });

                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception ex) {
                            }
                        }
                    });

                }
            }
        }).start();
    }

    private void share(Uri uri) throws IOException {

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setType("image/*");
        if (mFromCore) {
            shareIntent.setPackage(mPackageName);
        } else {
            shareIntent.setComponent(new ComponentName(mPackageName, mActivityName));
        }
        startActivity(shareIntent);
        finish();

        //just in case we got this from the camera, perform an early delete!
        TemporaryContentProvider.Companion.deleteUri(mImageUri);


    }


    private Uri transcode(Uri input, ImageXCoder coder, AbstractEncryptionParams encryptionParams, Intent encryptExtras)
            throws IOException, GeneralSecurityException, UserInteractionRequiredException, ContentNotFullyEmbeddedException {


        InputStream is = getContentResolver().openInputStream(input);
        ImageInfo origInfo = ImgUtil.INSTANCE.parseImageInfo(is);

        String origMimeType = "image/" + origInfo.getMimetype();
        is.close();

        byte[] content = null;


        if (mSampleSizeS >= 16) {
            mSampleSizeS = 2; //reset static sample size for next try
            return null;
        }


        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = mSampleSizeS;

        Bitmap resampledBm = BitmapFactory.decodeStream(getContentResolver().openInputStream(input), null, options);

        java.io.ByteArrayOutputStream baoss = new java.io.ByteArrayOutputStream();
        resampledBm.compress(Bitmap.CompressFormat.JPEG, RESAMPLE_QUALITY, baoss);
        baoss.close();
        content = baoss.toByteArray();

        Inner.InnerData.Builder idb = Inner.InnerData.newBuilder();
        Inner.ImageV0.Builder imageBuilder = idb.getImageV0Builder();
        imageBuilder.setMimetype(origMimeType);
        imageBuilder.setImage(ByteString.copyFrom(content));

        Inner.InnerData data = idb.build();


        Outer.Msg outer = CryptoHandlerFacade.Companion.getInstance(this).encrypt(data, encryptionParams, encryptExtras);


        Uri aUriResult = coder.encode(outer);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Util.INSTANCE.showToast(ImageEncryptActivity.this, getString(R.string.warning_image_resized, (100 / mSampleSizeS)));
            }
        });

        return aUriResult;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                finish(); //TODO implement retry
                break;
            }
        }
    }
}
