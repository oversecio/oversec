package io.oversec.one.ui;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import io.oversec.one.R;
import io.oversec.one.crypto.*;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.images.xcoder.ImageXCoder;
import io.oversec.one.crypto.images.xcoder.ImageXCoderFacade;
import io.oversec.one.crypto.proto.Outer;
import io.oversec.one.crypto.ui.AbstractBinaryEncryptionInfoFragment;
import io.oversec.one.crypto.ui.util.Util;
import io.oversec.one.iab.FullVersionListener;
import io.oversec.one.iab.IabUtil;
import io.oversec.one.ui.encparams.ActivityResultWrapper;
import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import roboguice.util.Ln;

import java.io.IOException;
import java.io.OutputStream;

public class ImageDecryptActivity extends AppCompatActivity {

    private static final int RQ_DECRYPT = 1;
    private static final String EXTRA_ZOOMED = "EXTRA_ZOOMED";
    private static final int RQ_UPGRADE = 8801;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 43;
    private ViewGroup mVgError;
    private ViewGroup mVgProgress;
    private ImageViewTouch mIvFull;
    private ImageView mIvThumb;
    private ViewGroup mVgMain;
    private Bitmap mBitmapRef;
    private Bitmap mBitmapPlain;
    private Bitmap mBitmapBlurred;
    private ActivityResultWrapper mTempActivityResult;
    private boolean mZoomedIn;
    private ViewGroup mVgUpgrade;
    private TextView mTvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decrypt_image);

        mVgError = (ViewGroup) findViewById(R.id.vg_error);
        mTvError = (TextView) findViewById(R.id.tv_error);
        mVgUpgrade = (ViewGroup) findViewById(R.id.vg_upgrade_reminder);
        mVgProgress = (ViewGroup) findViewById(R.id.vg_progress);
        mVgMain = (ViewGroup) findViewById(R.id.vg_main);
        mIvFull = (ImageViewTouch) findViewById(R.id.iv_full);
        mIvThumb = (ImageView) findViewById(R.id.iv_thumb);


        if (savedInstanceState != null) {
            mZoomedIn = savedInstanceState.getBoolean(EXTRA_ZOOMED);
        }
        init();
    }

    private void init() {
        mVgError.setVisibility(View.GONE);
        mVgUpgrade.setVisibility(View.GONE);
        mVgMain.setVisibility(View.GONE);
        mIvFull.setVisibility(View.GONE);
        mVgProgress.setVisibility(View.GONE);


        mVgProgress.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                handleReceiveImage(getIntent(), null);
            }
        }).start();

        handleActivityResult();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_image_decrypted, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return mBitmapRef != null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_send_plain) {
            share(Intent.ACTION_SEND);
        } else if (id == R.id.action_view_plain) {
            share(Intent.ACTION_VIEW);
        }
        return super.onOptionsItemSelected(item);
    }

    private void share(String action) {
        try {
            Uri uri = TemporaryContentProvider.Companion.prepare(this, "image/jpeg", TemporaryContentProvider.TTL_5_MINUTES, TemporaryContentProvider.TAG_DECRYPTED_IMAGE); //TODO make configurable
            OutputStream os = getContentResolver().openOutputStream(uri);
            mBitmapPlain.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();


            Intent shareIntent = new Intent();
            shareIntent.setAction(action);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.setType("image/*");

            Util.INSTANCE.share(this, shareIntent, null, getString(R.string.intent_chooser_share_unencrypted_image), true, OpenKeychainConnector.Companion.getInstance(this).allPackageNames(), false);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void handleReceiveImage(Intent intent, Intent decryptExtras) {
        //boolean isEncoded = false;
        Outer.Msg msg = null;
        BaseDecryptResult dr = null;
        ImageXCoder xcoder = null;
        Bitmap decodedBitmap = null;
        String error = null;

        Uri imageUri = intent.getData();

        if (imageUri == null) {
            imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (imageUri != null) {

            for (ImageXCoder xc : ImageXCoderFacade.INSTANCE.getAll(this)) {
                try {
                    msg = xc.parse(imageUri);
                    if (msg == null || msg.getMsgDataCase() == Outer.Msg.MsgDataCase.MSGDATA_NOT_SET) {
                        continue;
                    } else {
                        xcoder = xc;
                        break;
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    if (Util.INSTANCE.checkExternalStorageAccess(this, ex)) {
                        ActivityCompat.requestPermissions(ImageDecryptActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                        return;
                    }
                } catch (Exception exx) {
                    exx.printStackTrace();
                }
            }
        }


        if (xcoder == null || msg == null) {
            error = getString(R.string.error_image_decrypt_noencodeddata);
        } else {
            try {
                dr = CryptoHandlerFacade.Companion.getInstance(this).decrypt(msg, decryptExtras, null);
            } catch (UserInteractionRequiredException ex) {
                try {
                    startIntentSenderForResult(ex.getPendingIntent().getIntentSender(), RQ_DECRYPT, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    Ln.e(e, "error sending pending intent");
                }
                return;
            }

            if (dr != null && dr.isOk()) {
                try {
                    ByteString img = dr.getDecryptedDataAsInnerData().getImageV0().getImage();
                    byte[] buf = img.toByteArray();
                    decodedBitmap = BitmapFactory.decodeByteArray(buf, 0, buf.length);

                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                if (decodedBitmap == null) {
                    error = getString(R.string.error_image_decrypt_generic);
                }
            } else {

                error = getString(R.string.error_image_decrypt_nomatchingkey);
            }

        }


        if (error == null) {
            final Bitmap bm = decodedBitmap;
            final ImageXCoder xc = xcoder;
            final Outer.Msg outer = msg;
            final BaseDecryptResult dec = dr;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    publishResult_UI(bm, xc, outer, dec);
                }


            });
        } else {
            final String ferror = error;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    mTvError.setText(ferror);
                    mVgError.setVisibility(View.VISIBLE);
                    mVgMain.setVisibility(View.GONE);
                    mIvFull.setVisibility(View.GONE);
                    mVgProgress.setVisibility(View.GONE);
                }

            });
        }


    }


    private void handleActivityResult() {
        if (mTempActivityResult != null) {
            int requestCode = mTempActivityResult.getRequestCode();
            int resultCode = mTempActivityResult.getResultCode();
            Intent data = mTempActivityResult.getData();
            mTempActivityResult = null;

            if (requestCode == RQ_DECRYPT && resultCode == RESULT_OK) {
                handleReceiveImage(getIntent(), data);
            } else {
                if (requestCode == RQ_UPGRADE) {
                    init();
                }
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mTempActivityResult = new ActivityResultWrapper(requestCode, resultCode, data);

        //DAMNIT the liefecycle is indeed different whether activity is destroyed and recreated or just stays around!
        if (mVgMain != null) {
            handleActivityResult();
        } else {
            //will be handled in onCreateView
        }
    }

    private void publishResult_UI(final Bitmap bm, ImageXCoder xc, Outer.Msg outer, BaseDecryptResult dec) {
        try {
            mVgError.setVisibility(View.GONE);
            mVgMain.setVisibility(View.VISIBLE);
            mIvFull.setVisibility(View.GONE);
            mVgProgress.setVisibility(View.GONE);

            mBitmapPlain = bm;

            final Bitmap bmBlurred = Bitmap.createScaledBitmap(bm, 15, 15, true);
            //TODO recycle!!
            mBitmapBlurred = bmBlurred;

            mBitmapRef = bmBlurred;

            mVgUpgrade.setVisibility(View.VISIBLE);
            Button btUpgrade = (Button) findViewById(R.id.btn_upgrade);
            btUpgrade.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    IabUtil.getInstance(ImageDecryptActivity.this).showPurchaseActivity(ImageDecryptActivity.this, RQ_UPGRADE);
                }
            });
            mIvThumb.setImageBitmap(bmBlurred);

            mIvThumb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    zoomIn();
                }
            });

            if (mZoomedIn) {
                zoomIn();
            }

            AbstractBinaryEncryptionInfoFragment aFragment = null;
            AbstractCryptoHandler encryptionHandler = CryptoHandlerFacade.Companion.getInstance(this).getCryptoHandler(dec.getEncryptionMethod());
            if (encryptionHandler != null) {
                aFragment = encryptionHandler.getBinaryEncryptionInfoFragment(null);
            }

            if (aFragment == null) {
                finish();
            } else {

                aFragment.setArgs(null);

                android.app.FragmentManager manager = getFragmentManager();
                android.app.FragmentTransaction transaction = manager.beginTransaction();
                transaction.replace(io.oversec.one.crypto.R.id.encryptionInfoFragment_container, aFragment, "Foo");

                transaction.commit();


                aFragment.setData(outer, dec, xc);

            }


            IabUtil.getInstance(this).checkFullVersion(new FullVersionListener() {
                @Override
                public void onFullVersion_MAIN_THREAD(boolean isFullVersion) {
                    if (isFullVersion) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVgUpgrade.setVisibility(View.GONE);
                                mBitmapRef = bm;
                                mIvThumb.setImageBitmap(mBitmapPlain);
                            }
                        });
                    }
                }
            });


        } catch (Exception ex) {
            //TODO: implement better state synchronization,
            //weird stuff might happen if user rotates device while decrypting.
            Ln.e(ex, "damnit,");
        }
    }

    private void zoomIn() {
        mVgMain.setVisibility(View.GONE);
        mIvFull.setVisibility(View.VISIBLE);
        mIvFull.setImageBitmap(mBitmapRef, null, 0.1f, 100f);
        mIvFull.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);
    }

    @Override
    public void onBackPressed() {
        if (mIvFull.getVisibility() == View.VISIBLE) {
            mVgMain.setVisibility(View.VISIBLE);
            mIvFull.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_ZOOMED, mIvFull.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (mBitmapBlurred != null) {
            mBitmapBlurred.recycle();
        }
        if (mBitmapPlain != null) {
            mBitmapPlain.recycle();
        }
        super.onDestroy();
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
