package io.oversec.one.ui.encparams;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import io.oversec.one.Core;
import io.oversec.one.common.CoreContract;
import io.oversec.one.crypto.AbstractEncryptionParams;
import io.oversec.one.crypto.EncryptionMethod;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.encoding.XCoderAndPadder;
import io.oversec.one.crypto.encoding.pad.XCoderAndPadderFactory;
import io.oversec.one.crypto.sym.OversecKeystore2;
import io.oversec.one.crypto.sym.SymmetricEncryptionParams;
import io.oversec.one.crypto.sym.SymmetricKeyEncrypted;
import io.oversec.one.crypto.sym.ui.KeyImportCreateActivity;
import io.oversec.one.crypto.sym.ui.KeysFragment;
import io.oversec.one.crypto.sym.ui.SymmetricKeyRecyclerViewAdapter;
import io.oversec.one.crypto.ui.EncryptionParamsActivityContract;
import io.oversec.one.R;
import io.oversec.one.crypto.ui.AbstractEncryptionParamsFragment;
import io.oversec.one.crypto.ui.util.SimpleDividerItemDecoration;
import io.oversec.one.crypto.ui.util.StandaloneTooltipView;
import io.oversec.one.crypto.ui.util.XCoderAndPadderSpinnerAdapter;
import roboguice.util.Ln;

import java.util.ArrayList;
import java.util.List;

public class SymmetricEncryptionParamsFragment extends AbstractEncryptionParamsFragment implements OversecKeystore2.KeyStoreListener {
    private static final String EXTRA_PADDER_POS = "EXTRA_PADDER_POS";
    private static final String EXTRA_SELECTED_KEY_IDS = "EXTRA_SELECTED_KEY_IDS";
    private ImageButton mFabSymmetric;
    private Spinner mSpPaddingSym;
    private SymmetricKeyWithCheckboxRecyclerViewAdapter mKeysAdapter;
    private RecyclerView mRvSymmetricKeys;
    private OversecKeystore2 aKeystore;
    private ViewGroup mVgNoKey;
    private XCoderAndPadderSpinnerAdapter mSymPadderAdapter;
    private TextView mTvWarning;

    private ActivityResultWrapper mTempActivityResult;
    private CheckBox mCbAddLink;


    public static SymmetricEncryptionParamsFragment newInstance(String packagename, boolean isForTextEncryption, Bundle state) {
        SymmetricEncryptionParamsFragment fragment = new SymmetricEncryptionParamsFragment();
        fragment.setArgs(packagename, isForTextEncryption, state);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setMView(inflater.inflate(R.layout.sym_encryption_params, container, false));
        super.onCreateView(inflater, container, savedInstanceState);

        mVgNoKey = (ViewGroup) getMView().findViewById(R.id.compose_sym_vg_no_key);

        CoreContract core = CoreContract.Companion.getInstance();
        AbstractEncryptionParams initialParams = core.getBestEncryptionParams(getMPackageName());
        SymmetricEncryptionParams params = initialParams instanceof SymmetricEncryptionParams ? (SymmetricEncryptionParams) initialParams : null;


        setMTooltip((StandaloneTooltipView) getMView().findViewById(R.id.symParamsGotIt));
        getMTooltip().setArrowPosition(getMArrowPosition());
        getMTooltip().setVisibility(!getMHideToolTip() ? View.VISIBLE : View.GONE);

        mTvWarning = (TextView) getMView().findViewById(R.id.compose_sym_warning);

        mCbAddLink = (CheckBox) getMView().findViewById(R.id.cb_add_link);
        mCbAddLink.setVisibility(getMIsForTextEncryption() ? View.VISIBLE : View.GONE);

        mFabSymmetric = (ImageButton) getMView().findViewById(R.id.fab_sym);
        mFabSymmetric.getBackground().setColorFilter(Core.getInstance(getActivity()).getDb().getButtonOverlayBgColor(getMPackageName()), PorterDuff.Mode.SRC_ATOP);

        mFabSymmetric.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTvWarning.setText("");
                List<Long> ids = mKeysAdapter.getSelectedKeyIds();
                if (ids.size() > 0) {
                    doEncrypt(ids);
                } else {
                    mTvWarning.setText(R.string.warning_cant_encrypt_without_symkey);
                }
            }
        });

        mRvSymmetricKeys = (RecyclerView) getMView().findViewById(R.id.rv_keys);
        mRvSymmetricKeys.setLayoutManager(new LinearLayoutManager(getMView().getContext()));
        mRvSymmetricKeys.addItemDecoration(new SimpleDividerItemDecoration(getMView().getContext()));

        aKeystore = OversecKeystore2.Companion.getInstance(getMView().getContext());

        long[] selectedKeyIds = getArguments().getLongArray(EXTRA_SELECTED_KEY_IDS);
        List<Long> selectedKeyIdList = null;
        if (selectedKeyIds != null) {
            selectedKeyIdList = new ArrayList<>();
            for (long id : selectedKeyIds) {
                selectedKeyIdList.add(id);
            }
            updateList(selectedKeyIdList);
        } else {
            if (params != null) {
                selectedKeyIdList = params.getKeyIds();
            }
            updateList(selectedKeyIdList);
        }


        ImageButton btAddNewSymKey = (ImageButton) getMView().findViewById(R.id.compose_sym_btn_addkey);
        btAddNewSymKey.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                KeyImportCreateActivity.Companion.showAddKeyDialog(SymmetricEncryptionParamsFragment.this, EncryptionParamsActivityContract.REQUEST_CODE__CREATE_NEW_KEY);

            }
        });


        mSpPaddingSym = (Spinner) getMView().findViewById(R.id.spinner_sym_padding);
        mSymPadderAdapter = new XCoderAndPadderSpinnerAdapter(getMView().getContext(), XCoderAndPadderFactory.Companion.getInstance(container.getContext()).getSym(getMPackageName()));
        mSpPaddingSym.setAdapter(mSymPadderAdapter);

        int prevPos = getArguments().getInt(EXTRA_PADDER_POS, -1);
        if (prevPos > -1) {
            mSpPaddingSym.setSelection(prevPos);
        } else {
            mSpPaddingSym.setSelection(mSymPadderAdapter.getPositionFor(
                    getMContract().getXCoderId(EncryptionMethod.SYM, getMPackageName()),
                    getMContract().getPadderId(EncryptionMethod.SYM, getMPackageName())));
        }

        getMView().findViewById(R.id.compose_sym_vg_padding).setVisibility(getMIsForTextEncryption() ? View.VISIBLE : View.GONE);

        aKeystore.addListener(this);

        handleActivityResult();

        return getMView();
    }


    @Override
    public void onDestroyView() {
        aKeystore.removeListener(this);
        super.onDestroyView();
    }


    private void handleActivityResult() {
        if (mTempActivityResult != null) {
            int requestCode = mTempActivityResult.getRequestCode();
            int resultCode = mTempActivityResult.getResultCode();
            Intent data = mTempActivityResult.getData();
            mTempActivityResult = null;

            if (requestCode == EncryptionParamsActivityContract.REQUEST_CODE__CREATE_NEW_KEY) {
                if (resultCode == Activity.RESULT_OK) {

                    List<Long> ss = mKeysAdapter.getSelectedKeyIds();
                    if (data != null) {
                        long keyId = data.getLongExtra(KeysFragment.EXTRA_KEY_ID, 0);
                        ss.add(keyId);
                    }

                    updateList(ss);
                }
                updateList(mKeysAdapter.getSelectedKeyIds());
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

    @Override
    public String getTabTitle(Context ctx) {
        return ctx.getString(R.string.compose_tab__sym_title);
    }

    @Override
    public void onKeyStoreChanged() {
        getMView().post(new Runnable() {
            @Override
            public void run() {
                updateList(mKeysAdapter.getSelectedKeyIds());
            }
        });

    }

    private void updateList(List<Long> selectedKeys) {
        final boolean noKeys = aKeystore.isEmpty();
        mVgNoKey.setVisibility(noKeys ? View.VISIBLE : View.GONE);
        mKeysAdapter = getSymKeysAdapter(selectedKeys);
        mRvSymmetricKeys.setAdapter(mKeysAdapter);
    }

    class SymmetricKeyWithCheckboxRecyclerViewAdapter extends SymmetricKeyRecyclerViewAdapter {
        List<Long> mSelectedKeyIds = new ArrayList<>();

        public SymmetricKeyWithCheckboxRecyclerViewAdapter(Fragment fragment, List<SymmetricKeyEncrypted> items, List<Long> preselectedKeyIds, OversecKeystore2 keystore) {
            super(fragment, items);
            mSelectedKeyIds = preselectedKeyIds == null ? new ArrayList<Long>() : preselectedKeyIds;
        }


        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.sym_listitem_with_checkbox, parent, false);
            return new ViewHolderWithRadioButtonCheckBox(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            ViewHolderWithRadioButtonCheckBox myHolder = (ViewHolderWithRadioButtonCheckBox) holder;
            myHolder.mCheckBox.setChecked(mSelectedKeyIds.contains(getMKeys().get(position).getId()));
        }

        public List<Long> getSelectedKeyIds() {
            return mSelectedKeyIds;
        }


        private class ViewHolderWithRadioButtonCheckBox extends ViewHolder {
            public AppCompatCheckBox mCheckBox;

            public ViewHolderWithRadioButtonCheckBox(View view) {
                super(view);
                mCheckBox = (AppCompatCheckBox) view.findViewById(R.id.check);
                View.OnClickListener radioListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        long itemKeyId = getMKeys().get(getAdapterPosition()).getId();
                        if (mSelectedKeyIds.contains(itemKeyId)) {
                            mSelectedKeyIds.remove(itemKeyId);
                        } else {
                            mSelectedKeyIds.add(itemKeyId);
                        }
                        notifyItemRangeChanged(0, getMKeys().size());
                    }
                };

                mCheckBox.setOnClickListener(radioListener);

                getMView().setOnClickListener(null);
            }
        }
    }


    private void doEncrypt(List<Long> keyIds) {

        XCoderAndPadder x = mSymPadderAdapter.getItem(mSpPaddingSym.getSelectedItemPosition());
        getMContract().setXcoderAndPadder(EncryptionMethod.SYM, getMPackageName(), x.getCoderId(), x.getPadderId());

        getMContract().finishWithResultOk();
        getMContract().doEncrypt(new SymmetricEncryptionParams(keyIds, x.getXcoder().getId(), x.getPadder() == null ? null : x.getPadder().getId()), mCbAddLink.isChecked());
    }


    private SymmetricKeyWithCheckboxRecyclerViewAdapter getSymKeysAdapter(final List<Long> selectedKeys) {

        OversecKeystore2 aKeystore = OversecKeystore2.Companion.getInstance(getMView().getContext());
        List<SymmetricKeyEncrypted> keys = new ArrayList<>(aKeystore.getEncryptedKeys_sorted());

        if (selectedKeys != null) {
            for (Long id : selectedKeys) {

                int foundAt = -1;
                int i = 0;
                for (SymmetricKeyEncrypted k : keys) {
                    if (k.getId() == id) {
                        foundAt = i;
                        break;
                    }
                    i++;
                }

                if (foundAt >= 0) {
                    SymmetricKeyEncrypted kk = keys.get(foundAt);
                    keys.remove(foundAt);
                    keys.add(0, kk);
                } else {
                    selectedKeys.remove(id);
                }
            }
        }


        return new SymmetricKeyWithCheckboxRecyclerViewAdapter(SymmetricEncryptionParamsFragment.this, keys, selectedKeys, aKeystore);
    }


    @Override
    public EncryptionMethod getMethod() {
        return EncryptionMethod.SYM;
    }


    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.encparams_sym;
    }

    @Override
    public void saveState(Bundle b) {
        try {
            List<Long> keyIds = mKeysAdapter.getSelectedKeyIds();
            long[] a = new long[keyIds.size()];
            int i = 0;
            for (Long id : keyIds) {
                a[i] = id;
                i++;
            }

            b.putLongArray(EXTRA_SELECTED_KEY_IDS, a);

            b.putInt(EXTRA_PADDER_POS, mSpPaddingSym.getSelectedItemPosition());
        } catch (Exception ex) {
            Ln.e(ex, "error saving state!");
        }
    }
}
