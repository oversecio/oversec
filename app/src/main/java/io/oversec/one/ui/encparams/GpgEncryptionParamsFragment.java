package io.oversec.one.ui.encparams;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import io.oversec.one.BuildConfig;
import io.oversec.one.Core;
import io.oversec.one.common.CoreContract;
import io.oversec.one.crypto.AbstractEncryptionParams;
import io.oversec.one.crypto.AppsReceiver;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.EncryptionMethod;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.encoding.AsciiArmouredGpgXCoder;
import io.oversec.one.crypto.encoding.XCoderAndPadder;
import io.oversec.one.crypto.encoding.pad.XCoderAndPadderFactory;
import io.oversec.one.crypto.gpg.GpgCryptoHandler;
import io.oversec.one.crypto.gpg.GpgEncryptionParams;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.sym.SymUtil;
import io.oversec.one.crypto.ui.EncryptionParamsActivityContract;
import io.oversec.one.R;
import io.oversec.one.crypto.ui.AbstractEncryptionParamsFragment;
import io.oversec.one.crypto.ui.util.GotItPreferences;
import io.oversec.one.crypto.ui.util.StandaloneTooltipView;
import io.oversec.one.crypto.ui.util.XCoderAndPadderSpinnerAdapter;


import org.openintents.openpgp.util.OpenPgpApi;

import roboguice.util.Ln;

import java.util.ArrayList;
import java.util.List;

public class GpgEncryptionParamsFragment extends AbstractEncryptionParamsFragment implements AppsReceiver.IAppsReceiverListener {
    private static final String EXTRA_PUBLIC_KEYS = "EXTRA_PUBLIC_KEYS";
    private static final String EXTRA_SIGN = "EXTRA_SIGN";
    private static final String EXTRA_PADDER_POS = "EXTRA_PADDER_POS";
    private TextView mTvPgpOwnName;
    private TextView mTvPgpOwnId;
    private TextView mTvWarning;

    private ImageButton mFabPgp;

    private ImageButton mBtLookupOwn;
    private CheckBox mCbSign;
    private Spinner mSpPaddingPgp;
    private ListView mLvPgpList;
    private GpgEncryptionParams mEditorPgpEncryptionParams;

    private long mTempDownloadKeyId;
    private XCoderAndPadderSpinnerAdapter mGpgXcoderAndPadderAdapter;
    private ViewGroup mVgOkcOk, mVgOkcNo;
    private TextView mTvOkcStatus;
    private Button mBtnInstallPlay, mBtnInstallFdroid;

    private ActivityResultWrapper mTempActivityResult;
    private StandaloneTooltipView mTooltipSelectOwnKey;
    private int mTvPgpOwnNameTextColor;
    private CheckBox mCbAddLink;

    public static GpgEncryptionParamsFragment newInstance(String packagename, boolean isForTextEncryption, Bundle state) {
        GpgEncryptionParamsFragment fragment = new GpgEncryptionParamsFragment();
        fragment.setArgs(packagename, isForTextEncryption, state);
        return fragment;
    }

    public GpgEncryptionParamsFragment() {
        //Ln.d("Construct");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setMView(inflater.inflate(R.layout.gpg_encryption_params, container, false));
        super.onCreateView(inflater, container, savedInstanceState);


        CoreContract core = CoreContract.Companion.getInstance();
        CryptoHandlerFacade encryptionFacade = CryptoHandlerFacade.Companion.getInstance(getMView().getContext());
        final GpgCryptoHandler aGpgEncryptionhandler = getGpgEncryptionHandler(getMView().getContext());

        AbstractEncryptionParams initialParams = core.getBestEncryptionParams(getMPackageName());

        setMTooltip((StandaloneTooltipView) getMView().findViewById(R.id.gpgParamsGotIt));
        getMTooltip().setArrowPosition(getMArrowPosition());
        getMTooltip().setVisibility(!getMHideToolTip() ? View.VISIBLE : View.GONE);

        mTooltipSelectOwnKey = (StandaloneTooltipView) getMView().findViewById(R.id.select_ownkey_tooltip);

        mVgOkcOk = (ViewGroup) getMView().findViewById(R.id.content_okc);
        mVgOkcNo = (ViewGroup) getMView().findViewById(R.id.content_no_okc);
        mTvOkcStatus = (TextView) getMView().findViewById(R.id.okc_status_msg);
        mBtnInstallPlay = (Button) getMView().findViewById(R.id.okc_btn_play);
        mBtnInstallFdroid = (Button) getMView().findViewById(R.id.okc_btn_fdroid);

        mLvPgpList = (ListView) getMView().findViewById(R.id.compose_pgp_lv_recipients);
        mCbSign = (CheckBox) getMView().findViewById(R.id.cb_pgp_sign);
        mFabPgp = (ImageButton) getMView().findViewById(R.id.fabb_pgp);
        mFabPgp.getBackground().setColorFilter(Core.getInstance(getActivity()).getDb().getButtonOverlayBgColor(getMPackageName()), PorterDuff.Mode.SRC_ATOP);

        int okcVersion = OpenKeychainConnector.Companion.getInstance(container.getContext()).getVersion();

        if (initialParams != null && initialParams instanceof GpgEncryptionParams) {
            mEditorPgpEncryptionParams = (GpgEncryptionParams) initialParams;
        } else {
            mEditorPgpEncryptionParams = new GpgEncryptionParams(new long[0], AsciiArmouredGpgXCoder.ID, null);
        }


        mCbSign.setChecked(getArguments().getBoolean(EXTRA_SIGN,
                mEditorPgpEncryptionParams.isSign()));


        long[] stateSelected = getArguments().getLongArray(EXTRA_PUBLIC_KEYS);
        if (stateSelected != null) {
            mEditorPgpEncryptionParams.setPublicKeyIds(stateSelected);
        }


        if (aGpgEncryptionhandler.getGpgOwnPublicKeyId() != 0) {
            mEditorPgpEncryptionParams.setOwnPublicKey(aGpgEncryptionhandler.getGpgOwnPublicKeyId());

        }


        mFabPgp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditorPgpEncryptionParams.setSign(mCbSign.isChecked());
                if (!verifyPgpEncryptionParams()) {
                    return;
                }

                if (GotItPreferences.Companion.getPreferences(getActivity()).isTooltipConfirmed(getString(R.string.tooltipid_pgpmessagesarehuge))) {
                    doEncrypt(mEditorPgpEncryptionParams);
                } else {
                    new MaterialDialog.Builder(getActivity())
                            .title(R.string.warning)
                            .content(R.string.warning_pgp_messages_are_big)
                            .positiveText(R.string.action_gotit)
                            .neutralText(R.string.action_moreinfo)
                            .negativeText(R.string.action_cancel)
                            .cancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    dialog.dismiss();
                                }
                            })
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    GotItPreferences.Companion.getPreferences(getActivity()).setTooltipConfirmed(getString(R.string.tooltipid_pgpmessagesarehuge));
                                    doEncrypt(mEditorPgpEncryptionParams);
                                }
                            })
                            .onNegative(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    dialog.dismiss();
                                }
                            })
                            .onNeutral(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                    Help.INSTANCE.open(getActivity(), Help.ANCHOR.encparams_pgp);
                                }
                            })
                            .show();
                }


            }
        });


        mBtLookupOwn = (ImageButton) getMView().findViewById(R.id.compose_pgp_btn_own_key);


        ImageButton btAddRecipient = (ImageButton) getMView().findViewById(R.id.compose_pgp_btn_add_recipient);

        mTvPgpOwnName = (TextView) getMView().findViewById(R.id.compose_pgp_tv_own_key_name);
        mTvPgpOwnNameTextColor = mTvPgpOwnName.getCurrentTextColor();
        mTvPgpOwnId = (TextView) getMView().findViewById(R.id.compose_pgp_tv_own_key_hex);
        mTvWarning = (TextView) getMView().findViewById(R.id.compose_pgp_warning);


        updatePgpOwnKey();

        mBtLookupOwn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                triggerSigningKeySelection(null);

            }
        });
        btAddRecipient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerRecipientSelection(EncryptionParamsActivityContract.REQUEST_CODE_RECIPIENT_SELECTION, null);
            }
        });
        updatePgpRecipientsList();

        mCbAddLink = (CheckBox) getMView().findViewById(R.id.cb_add_link);
        mCbAddLink.setVisibility(getMIsForTextEncryption() ? View.VISIBLE : View.GONE);
        mSpPaddingPgp = (Spinner) getMView().findViewById(R.id.spinner_pgp_padding);
        mGpgXcoderAndPadderAdapter = new XCoderAndPadderSpinnerAdapter(getMView().getContext(), XCoderAndPadderFactory.Companion.getInstance(container.getContext()).getGpg(getMPackageName()));
        mSpPaddingPgp.setAdapter(mGpgXcoderAndPadderAdapter);

        int prevPos = getArguments().getInt(EXTRA_PADDER_POS, -1);
        if (prevPos > -1) {
            mSpPaddingPgp.setSelection(prevPos);
        } else {
            mSpPaddingPgp.setSelection(mGpgXcoderAndPadderAdapter.getPositionFor(
                    getMContract().getXCoderId(EncryptionMethod.GPG, getMPackageName()),
                    getMContract().getPadderId(EncryptionMethod.GPG, getMPackageName())
            ));
        }


        mSpPaddingPgp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                XCoderAndPadder x = mGpgXcoderAndPadderAdapter.getItem(position);
                mEditorPgpEncryptionParams.setXcoderAndPadder(x.getXcoder(), x.getPadder());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        verifyPgpEncryptionParams();

        getMView().findViewById(R.id.compose_pgp_vg_padding).setVisibility(getMIsForTextEncryption() ? View.VISIBLE : View.GONE);

        AppsReceiver.Companion.addListener(this);

        checkVisibilities();

        handleActivityResult();

        return getMView();
    }


    private void checkVisibilities() {
        int okcVersion = OpenKeychainConnector.Companion.getInstance(getActivity()).getVersion();
        if (okcVersion >= OpenKeychainConnector.V_MIN) {
            mVgOkcOk.setVisibility(View.VISIBLE);
            mVgOkcNo.setVisibility(View.INVISIBLE);
        } else {
            if (okcVersion == -1) {
                mTvOkcStatus.setText(R.string.okc_not_installed);
            } else {
                mTvOkcStatus.setText(getString(R.string.okc_installed_but_too_old, OpenKeychainConnector.Companion.getInstance(getActivity()).getVersionName()));
            }

            mBtnInstallPlay.setVisibility(OpenKeychainConnector.Companion.getInstance(getActivity()).isGooglePlayInstalled() ? View.VISIBLE : View.GONE);

            mBtnInstallFdroid.setVisibility(BuildConfig.IS_FRDOID?View.VISIBLE:View.GONE);

            if (mBtnInstallPlay.getVisibility() == View.VISIBLE) {
                mBtnInstallPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        OpenKeychainConnector.Companion.getInstance(getActivity()).openInPlayStore();
                    }
                });

            }

            if (mBtnInstallFdroid.getVisibility() == View.VISIBLE) {
                mBtnInstallFdroid.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        OpenKeychainConnector.Companion.getInstance(getActivity()).openInFdroid();
                    }
                });

            }

            mVgOkcOk.setVisibility(View.INVISIBLE);
            mVgOkcNo.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        AppsReceiver.Companion.removeListener(this);
        super.onDestroyView();
    }

    private void handleActivityResult() {
        if (mTempActivityResult != null) {
            int requestCode = mTempActivityResult.getRequestCode();
            int resultCode = mTempActivityResult.getResultCode();
            Intent data = mTempActivityResult.getData();
            mTempActivityResult = null;


            if (requestCode == EncryptionParamsActivityContract.REQUEST_CODE_DOWNLOAD_KEY) {
                if (resultCode == Activity.RESULT_OK) {
                    handleDownloadedKey(getActivity(), mTempDownloadKeyId, data);
                } else {
                    // Ln.w("user cancelled pendingintent activity");
                }
            } else if (requestCode == EncryptionParamsActivityContract.REQUEST_CODE_RECIPIENT_SELECTION) {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        if (data.hasExtra(OpenPgpApi.EXTRA_KEY_IDS) || data.hasExtra("key_ids_selected"/*OpenPgpApi.EXTRA_KEY_IDS_SELECTED*/)) {
                            long[] keyIds = new long[0];
                            if (data.hasExtra(OpenPgpApi.EXTRA_KEY_IDS)) {
                                keyIds = data.getLongArrayExtra(OpenPgpApi.EXTRA_KEY_IDS);
                            } else if (data.hasExtra("key_ids_selected"/*OpenPgpApi.EXTRA_KEY_IDS_SELECTED*/)) {
                                keyIds = data.getLongArrayExtra("key_ids_selected"/*OpenPgpApi.EXTRA_KEY_IDS_SELECTED*/);
                            }
                            //not sure why Anal Studio / Gradle can't find the new EXTRA_KEY_IDS_SELECTED constant
                            //also not sure why they had to change it, but well, need to check for both

                            Long[] keys = new Long[keyIds.length];
                            for (int i = 0; i < keyIds.length; i++) {
                                keys[i] = keyIds[i];
                            }
                            if (keyIds != null && keyIds.length > 0) {

                                mEditorPgpEncryptionParams.addPublicKeyIds(keys, getGpgEncryptionHandler(getActivity()).getGpgOwnPublicKeyId());
                                updatePgpRecipientsList();
                            }
                        } else {
                            triggerRecipientSelection(EncryptionParamsActivityContract.REQUEST_CODE_RECIPIENT_SELECTION, data);
                        }
                    } else {
                        //Ln.w("REQUEST_CODE_RECIPIENT_SELECTION returned without data");
                    }

                } else {
                    // Ln.w("REQUEST_CODE_RECIPIENT_SELECTION returned with result code %s" , resultCode);
                }
            } else if (requestCode == EncryptionParamsActivityContract.REQUEST_CODE_OWNSIGNATUREKEY_SELECTION) {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        if (data.hasExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
                            long signKeyId = data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0);
                            mEditorPgpEncryptionParams.setOwnPublicKey(signKeyId);
                            getGpgEncryptionHandler(getActivity()).setGpgOwnPublicKeyId(signKeyId);
                            updatePgpOwnKey();
                            updatePgpRecipientsList();

                        } else {
                            triggerSigningKeySelection(data);
                        }
                    } else {
                        // Ln.w("ACTION_GET_SIGN_KEY_ID returned without data");
                    }

                } else {
                    //   Ln.w("ACTION_GET_SIGN_KEY_ID returned with result code %s", resultCode);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mTempActivityResult = new ActivityResultWrapper(requestCode, resultCode, data);

        //DAMNIT the liefecycle is indeed different whether activity os destroyed andrecreated or just stays around!
        if (getMView() != null) {
            handleActivityResult();
        } else {
            //will be handled in onCreateView
        }
    }

    private void handleDownloadedKey(Activity activity, long keyId, Intent data) {

        Long mainKeyId = getGpgEncryptionHandler(activity).getMainKeyIdFromSubkeyId(keyId);
        if (mainKeyId != null) {
            mEditorPgpEncryptionParams.removePublicKey(keyId);
            mEditorPgpEncryptionParams.addPublicKey(mainKeyId);
        }

        mTempDownloadKeyId = 0;


        updatePgpOwnKey();
        updatePgpRecipientsList();

    }

    private void updatePgpOwnKey() {
        long keyId = getGpgEncryptionHandler(getMView().getContext()).getGpgOwnPublicKeyId();
        if (keyId == 0) {
            mTvPgpOwnName.setText(R.string.compose_msg_noownkey_title);
            mTvPgpOwnId.setText(R.string.compose_msg_noownkey_sub);
            mBtLookupOwn.setVisibility(View.VISIBLE);
            mCbSign.setChecked(false);
            mTooltipSelectOwnKey.setVisibility(View.VISIBLE);
            mTvPgpOwnName.setTextColor(ContextCompat.getColor(getActivity(), R.color.text_warning));

        } else {

            String label = getGpgEncryptionHandler(getMView().getContext()).getFirstUserIDByKeyId(keyId, null);
            if (label != null) {
                mTvPgpOwnName.setText(label);
                mTvPgpOwnId.setText(SymUtil.INSTANCE.longToPrettyHex(keyId));
                mBtLookupOwn.setVisibility(View.GONE);

            } else {
                mBtLookupOwn.setVisibility(View.VISIBLE);
                mTvPgpOwnName.setText(R.string.compose_msg_noownkey_title);
                mTvPgpOwnId.setText(R.string.compose_msg_noownkey_sub);
            }

            mTooltipSelectOwnKey.setVisibility(View.GONE);

            mTvPgpOwnName.setTextColor(mTvPgpOwnNameTextColor);

        }
        verifyPgpEncryptionParams();
    }


    private boolean verifyPgpEncryptionParams() {
        boolean res = true;

        long ownKeyId = getGpgEncryptionHandler(getMView().getContext()).getGpgOwnPublicKeyId();
        mCbSign.setVisibility(ownKeyId == 0 ? View.GONE : View.VISIBLE);


        mTvWarning.setText("");
        if (mEditorPgpEncryptionParams.getAllPublicKeyIds().length == 0) {
            mTvWarning.setText(R.string.warning_cant_encrypt_without_recipient);
            return false;
        } else if (mEditorPgpEncryptionParams.getAllPublicKeyIds().length == 1) {
            if (ownKeyId == 0) {
                mTvWarning.setText(R.string.warning_encrypt_not_self);

            } else {
                mTvWarning.setText(R.string.warning_no_recipients);

            }
        }

        for (Long keyId : mEditorPgpEncryptionParams.getAllPublicKeyIds()) {


            if (getGpgEncryptionHandler(getMView().getContext()).getFirstUserIDByKeyId(keyId, null) == null) {
                mTvWarning.setText(R.string.warning_cant_encrypt_one_or_more_keys_missing);
                res = false;
                break;

            }


        }
        manageFab(res);

        return res;
    }

    private void manageFab(boolean pgpEncryptionParamsAreValid) {
        mFabPgp.setVisibility(pgpEncryptionParamsAreValid ? View.VISIBLE : View.INVISIBLE);
    }


    private void updatePgpRecipientsList() {
        ArrayAdapter<Long> recipientsAdapter = getPgpRecipientsAdapter();
        mLvPgpList.setAdapter(recipientsAdapter);
        verifyPgpEncryptionParams();
    }

    private ArrayAdapter<Long> getPgpRecipientsAdapter() {

        ArrayAdapter<Long> res = null;

        //OversecKeystore aKeystore = OversecKeystore.getInstance();
        List<Long> keys = new ArrayList<>();


        keys.addAll(mEditorPgpEncryptionParams.getPublicKeyIds());

        if (getGpgEncryptionHandler(getMView().getContext()).getGpgOwnPublicKeyId() != 0) {
            keys.remove(getGpgEncryptionHandler(getMView().getContext()).getGpgOwnPublicKeyId());
        }


        //TODO: maybe sort

        res = new ArrayAdapter<Long>(getMView().getContext(),
                R.layout.gpg_listitem_recipient, R.id.tv_title, keys) {

            @Override
            public View getView(int position, View v, ViewGroup parent) {

                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(
                            R.layout.gpg_listitem_recipient, null);
                }
                final Long item = getItem(position);

                fillListItemViewPgpRecipient(v, item);

                return v;
            }

        };

        return res;
    }

    //TODO: use recycler view
    private void fillListItemViewPgpRecipient(View v, final Long keyId) {
        TextView tvName = (TextView) v.findViewById(R.id.tv_title);
        TextView tvId = (TextView) v.findViewById(R.id.tv_subtitle);
        ImageButton btnDelete = (ImageButton) v.findViewById(R.id.btn_delete);
        ImageButton btnDownload = (ImageButton) v.findViewById(R.id.btn_download);
        btnDownload.setVisibility(View.GONE);

        String name = getGpgEncryptionHandler(getMView().getContext()).getFirstUserIDByKeyId(keyId, null);


        tvName.setText(name != null ? name : v.getContext().getString(R.string.action_download_missing_public_key));

        tvName.setTextColor(getPrimaryOrWarningTextColor(name != null));
        tvId.setText(SymUtil.INSTANCE.longToPrettyHex(keyId));

        if (name == null) {
            btnDownload.setVisibility(View.VISIBLE);
            btnDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    downloadKey(keyId, null);
                }
            });
        }

        btnDelete.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mEditorPgpEncryptionParams.removePublicKey(keyId);
                updatePgpRecipientsList();
            }
        });
    }

    private int getPrimaryOrWarningTextColor(boolean b) {
        return ContextCompat.getColor(getActivity(), b ? android.R.color.primary_text_light : R.color.colorWarning);
    }


    private synchronized void downloadKey(final long keyId, Intent actionIntent) {
        PendingIntent pi = getGpgEncryptionHandler(getMView().getContext()).getDownloadKeyPendingIntent(keyId, actionIntent);

        if (pi != null) {

            mTempDownloadKeyId = keyId;
            try {
                getActivity().startIntentSenderForResult(pi.getIntentSender(), EncryptionParamsActivityContract.REQUEST_CODE_DOWNLOAD_KEY, null, 0, 0, 0);

            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                //TODO what now?
            }

        }

    }

    public void triggerRecipientSelection(int requestCode, Intent actionIntent) {
        PendingIntent pi = getGpgEncryptionHandler(getMView().getContext()).triggerRecipientSelection(actionIntent);
        if (pi != null) {
            try {
                getActivity().startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                //TODO now what?
            }


        } else {
            //TODO really needed?   recipientsSelectedCallback.onRecipientsSelected(null);
        }
    }

    public void triggerSigningKeySelection(Intent actionIntent) {
        PendingIntent pi = getGpgEncryptionHandler(getMView().getContext()).triggerSigningKeySelection(actionIntent);
        if (pi != null) {
            try {
                getActivity().startIntentSenderForResult(pi.getIntentSender(), EncryptionParamsActivityContract.REQUEST_CODE_OWNSIGNATUREKEY_SELECTION, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                //TODO now what?
            }


        } else {
            //TODO really needed?   recipientsSelectedCallback.onRecipientsSelected(null);
        }
    }


    private void doEncrypt(GpgEncryptionParams pp) {
        getMContract().setXcoderAndPadder(EncryptionMethod.GPG, getMPackageName(), mEditorPgpEncryptionParams.getCoderId(), mEditorPgpEncryptionParams.getPadderId());

        getMContract().finishWithResultOk();
        getMContract().doEncrypt(pp, mCbAddLink.isChecked());
    }

    private GpgCryptoHandler getGpgEncryptionHandler(Context ctx) {


        CryptoHandlerFacade encryptionFacade = CryptoHandlerFacade.Companion.getInstance(ctx);
        return (GpgCryptoHandler) encryptionFacade.getCryptoHandler(EncryptionMethod.GPG);
    }

    @Override
    public String getTabTitle(Context ctx) {
        return ctx.getString(R.string.compose_tab__gpg_title);
    }


    @Override
    public EncryptionMethod getMethod() {
        return EncryptionMethod.GPG;
    }


    @Override
    public void onAppChanged(Context ctx, String action, String packagename) {
        checkVisibilities();
    }


    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.encparams_pgp;
    }

    @Override
    public void saveState(Bundle b) {
        try {
            b.putLongArray(EXTRA_PUBLIC_KEYS,
                    mEditorPgpEncryptionParams.getAllPublicKeyIds());

            b.putBoolean(EXTRA_SIGN, mCbSign.isChecked());

            b.putInt(EXTRA_PADDER_POS, mSpPaddingPgp.getSelectedItemPosition());
        } catch (Exception ex) {
            Ln.e(ex, "error saving state!");
        }
    }

}
