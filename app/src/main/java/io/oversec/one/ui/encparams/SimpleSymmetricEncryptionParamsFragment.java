package io.oversec.one.ui.encparams;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
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
import io.oversec.one.R;
import io.oversec.one.crypto.encoding.XCoderAndPadder;
import io.oversec.one.crypto.encoding.pad.XCoderAndPadderFactory;
import io.oversec.one.crypto.sym.SymUtil;
import io.oversec.one.crypto.sym.SymmetricKeyPlain;
import io.oversec.one.crypto.symbase.KeyCache;
import io.oversec.one.crypto.symbase.OversecKeyCacheListener;
import io.oversec.one.crypto.symsimple.SimpleSymmetricEncryptionParams;
import io.oversec.one.crypto.symsimple.ui.AddPasswordKeyActivity;
import io.oversec.one.crypto.ui.AbstractEncryptionParamsFragment;
import io.oversec.one.crypto.ui.util.SimpleDividerItemDecoration;
import io.oversec.one.crypto.ui.util.StandaloneTooltipView;
import io.oversec.one.crypto.ui.util.XCoderAndPadderSpinnerAdapter;
import roboguice.util.Ln;

import java.util.*;

public class SimpleSymmetricEncryptionParamsFragment extends AbstractEncryptionParamsFragment implements OversecKeyCacheListener {
    private static final int RQ_ADD_ENCRYPTION_PASSWORD = 8000;
    private static final int RQ_UPGRADE = 8010;

    private static final String EXTRA_SELECTED_KEY_IDS = "EXTRA_SELECTED_KEY_IDS";
    private static final String EXTRA_PADDER_POS = "EXTRA_PADDER_POS";
    private ImageButton mFabSymmetric;
    private Spinner mSpPaddingSym;
    private SimpleSymmetricKeyRecyclerViewAdapter mKeysAdapter;
    private RecyclerView mRvPasswords;

    private XCoderAndPadderSpinnerAdapter mSymPadderAdapter;
    private TextView mTvWarning;
    private ImageButton mBtnSimpleSymParamsEnterPassword;
    private KeyCache mKeyCache;
    private SimpleSymmetricEncryptionParams mParams;

    private ActivityResultWrapper mTempActivityResult;
    private CheckBox mCbAddLink;

    public static SimpleSymmetricEncryptionParamsFragment newInstance(String packagename, boolean isForTextEncryption, Bundle state) {
        SimpleSymmetricEncryptionParamsFragment fragment = new SimpleSymmetricEncryptionParamsFragment();
        fragment.setArgs(packagename, isForTextEncryption, state);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setMView(inflater.inflate(R.layout.simplesym_encryption_params, container, false));
        super.onCreateView(inflater, container, savedInstanceState);

        mKeyCache = KeyCache.Companion.getInstance(container.getContext());

        CoreContract core = CoreContract.Companion.getInstance();
        AbstractEncryptionParams initialParams = core.getBestEncryptionParams(getMPackageName());
        mParams = initialParams instanceof SimpleSymmetricEncryptionParams ? (SimpleSymmetricEncryptionParams) initialParams : null;

        setMTooltip((StandaloneTooltipView) getMView().findViewById(R.id.simpleSymParamsGotIt));
        getMTooltip().setArrowPosition(getMArrowPosition());
        getMTooltip().setVisibility(!getMHideToolTip() ? View.VISIBLE : View.GONE);

        mBtnSimpleSymParamsEnterPassword = (ImageButton) getMView().findViewById(R.id.btnSimpleSymParamsEnterPassword);
        mBtnSimpleSymParamsEnterPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddNewPasswordIfFullVersion();
            }
        });


        mTvWarning = (TextView) getMView().findViewById(R.id.compose_sym_warning);

        mFabSymmetric = (ImageButton) getMView().findViewById(R.id.fab_sym);
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

        mFabSymmetric.getBackground().setColorFilter(Core.getInstance(getActivity()).getDb().getButtonOverlayBgColor(getMPackageName()), PorterDuff.Mode.SRC_ATOP);

        mRvPasswords = (RecyclerView) getMView().findViewById(R.id.rv_keys);
        mRvPasswords.setLayoutManager(new LinearLayoutManager(getMView().getContext()));
        mRvPasswords.addItemDecoration(new SimpleDividerItemDecoration(getMView().getContext()));


        long[] selectedKeyIds = getArguments().getLongArray(EXTRA_SELECTED_KEY_IDS);
        List<Long> selectedKeyIdList = null;
        if (selectedKeyIds != null) {
            selectedKeyIdList = Collections.synchronizedList(new ArrayList<Long>());
            for (long id : selectedKeyIds) {
                selectedKeyIdList.add(id);
            }
            updateList(selectedKeyIdList, false);
        } else {
            if (mParams != null) {
                selectedKeyIdList = mParams.getKeyIds();
            }
            updateList(selectedKeyIdList, true);
        }

        mCbAddLink = (CheckBox) getMView().findViewById(R.id.cb_add_link);
        mCbAddLink.setVisibility(getMIsForTextEncryption() ? View.VISIBLE : View.GONE);

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

        mKeyCache.addKeyCacheListener(this);


        handleActivityResult();

        return getMView();
    }


    @Override
    public void onDestroyView() {
        mKeyCache.removeKeyCacheListener(this);
        super.onDestroyView();
    }

    private void showAddNewPasswordIfFullVersion() {

        if (mKeysAdapter != null && mKeysAdapter.getItemCount() >= getResources().getInteger(R.integer.feature_max_num_passwords)) {

            CoreContract.Companion.getInstance().doIfFullVersionOrShowPurchaseDialog(SimpleSymmetricEncryptionParamsFragment.this, new Runnable() {
                @Override
                public void run() {
                    showAddPasswordKeyActivity(SimpleSymmetricEncryptionParamsFragment.this);
                }
            }, RQ_UPGRADE);
        } else {
            showAddPasswordKeyActivity(SimpleSymmetricEncryptionParamsFragment.this);
        }
    }

    private static void showAddPasswordKeyActivity(SimpleSymmetricEncryptionParamsFragment frag) {
        AddPasswordKeyActivity.Companion.showForResult(frag, RQ_ADD_ENCRYPTION_PASSWORD);
    }

    private void updateList(List<Long> selectedKeyIds, boolean reorderSelectedToFront) {

        mKeysAdapter = getSymKeysAdapter(selectedKeyIds, reorderSelectedToFront);
        mRvPasswords.setAdapter(mKeysAdapter);
    }

    private void handleActivityResult() {
        if (mTempActivityResult != null) {
            int requestCode = mTempActivityResult.getRequestCode();
            int resultCode = mTempActivityResult.getResultCode();
            Intent data = mTempActivityResult.getData();
            mTempActivityResult = null;

            if (requestCode == RQ_ADD_ENCRYPTION_PASSWORD) {
                if (resultCode == Activity.RESULT_OK) {
                    List<Long> ss = mKeysAdapter.getSelectedKeyIds();
                    if (data != null) {
                        long keyId = data.getLongExtra(AddPasswordKeyActivity.EXTRA_RESULT_KEY_ID, 0);
                        ss.add(keyId);
                    }

                    updateList(ss, false);
                }

            }
        }

        View tooltip = getMView().findViewById(R.id.simpleSymParamsGotItAppSec);
        tooltip.setVisibility(mKeysAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
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
        return ctx.getString(R.string.compose_tab__simplesym_title);
    }

    @Override
    public void onFinishedCachingKey(long keyId) {
        getMView().post(new Runnable() {
            @Override
            public void run() {
                updateList(mKeysAdapter.getSelectedKeyIds(), false);
            }
        });

    }

    @Override
    public void onStartedCachingKey(long keyId) {

    }


    class SimpleSymmetricKeyRecyclerViewAdapter extends RecyclerView.Adapter<SimpleSymmetricKeyRecyclerViewAdapter.ViewHolder> {
        private List<Long> mSelectedKeyIds = Collections.synchronizedList(new ArrayList<Long>());
        private List<SymmetricKeyPlain> mKeys;

        public SimpleSymmetricKeyRecyclerViewAdapter(List<SymmetricKeyPlain> items, List<Long> preselectedKeyIds) {
            mKeys = items;
            mSelectedKeyIds = preselectedKeyIds != null ? preselectedKeyIds : Collections.synchronizedList(new ArrayList<Long>());
        }


        @Override
        public int getItemCount() {
            return mKeys.size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.simplesym_listitem, parent, false);
            return new ViewHolder(view);
        }


        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {


            SymmetricKeyPlain key = mKeys.get(position);

            holder.mCheckBox.setChecked(mSelectedKeyIds.contains(key.getId()));

            Date createdDate = key.getCreatedDate();

            holder.mTv1.setText(key.getName());

            Date now = new Date();
            holder.mTv2.setText(getString(R.string.key_age, DateUtils.getRelativeTimeSpanString(createdDate.getTime(), now.getTime(), DateUtils.MINUTE_IN_MILLIS)));

            SymUtil.INSTANCE.applyAvatar(holder.mIvAvatar, key.getId(), key.getName());
        }

        public List<Long> getSelectedKeyIds() {
            return mSelectedKeyIds;
        }


        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton mBtDelete;
            private final TextView mIvAvatar;
            private final TextView mTv1, mTv2;
            public AppCompatCheckBox mCheckBox;

            public ViewHolder(View view) {
                super(view);
                mTv1 = (TextView) view.findViewById(R.id.tv1);
                mTv2 = (TextView) view.findViewById(R.id.tv2);

                mCheckBox = (AppCompatCheckBox) view.findViewById(R.id.check);
                View.OnClickListener radioListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        long itemKeyId = mKeys.get(getAdapterPosition()).getId();
                        if (mSelectedKeyIds.contains(itemKeyId)) {
                            mSelectedKeyIds.remove(itemKeyId);
                        } else {
                            mSelectedKeyIds.add(itemKeyId);
                        }
                        notifyItemRangeChanged(0, mKeys.size());
                    }
                };

                mCheckBox.setOnClickListener(radioListener);


                mBtDelete = (ImageButton) view.findViewById(R.id.ibDelete);
                mIvAvatar = (TextView) view.findViewById(R.id.tvAvatar);

                mBtDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        long itemKeyId = mKeys.get(getAdapterPosition()).getId();
                        mKeyCache.expire(itemKeyId);
                        updateList(mKeysAdapter.getSelectedKeyIds(), false);
                    }
                });

                //TODO avatar pic

                getMView().setOnClickListener(null);
            }
        }
    }


    private void doEncrypt(List<Long> keyIds) {

        XCoderAndPadder x = mSymPadderAdapter.getItem(mSpPaddingSym.getSelectedItemPosition());
        getMContract().setXcoderAndPadder(EncryptionMethod.SIMPLESYM, getMPackageName(), x.getCoderId(), x.getPadderId());

        getMContract().finishWithResultOk();
        getMContract().doEncrypt(new SimpleSymmetricEncryptionParams(keyIds, x.getXcoder().getId(), x.getPadder() == null ? null : x.getPadder().getId()), mCbAddLink.isChecked());
    }


    private SimpleSymmetricKeyRecyclerViewAdapter getSymKeysAdapter(final List<Long> selectedKeyIds, boolean reorderSelectedKeyIdsToFront) {

        List<SymmetricKeyPlain> keys = new ArrayList<>(mKeyCache.getAllSimpleKeys());

        if (reorderSelectedKeyIdsToFront) {
            if (selectedKeyIds != null) {
                synchronized (selectedKeyIds) {
                    Set<Long> notLongerPresent = new HashSet<>();
                    for (Long id : selectedKeyIds) {

                        int foundAt = -1;
                        int i = 0;
                        for (SymmetricKeyPlain k : keys) {
                            if (k.getId() == id) {
                                foundAt = i;
                                break;
                            }
                            i++;
                        }

                        if (foundAt >= 0) {
                            SymmetricKeyPlain kk = keys.get(foundAt);
                            keys.remove(foundAt);
                            keys.add(0, kk);
                        } else {
                            notLongerPresent.add(id);

                        }
                    }
                    for (Long id : notLongerPresent) {
                        selectedKeyIds.remove(id);
                    }
                }
            }
        }

        return new SimpleSymmetricKeyRecyclerViewAdapter(keys, selectedKeyIds);
    }


    @Override
    public EncryptionMethod getMethod() {
        return EncryptionMethod.SIMPLESYM;
    }


    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.encparams_simple;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void saveState(Bundle b) {
        try {
            List<Long> keyIds = mKeysAdapter == null ? Collections.synchronizedList(new ArrayList<Long>()) : mKeysAdapter.getSelectedKeyIds();
            long[] a = new long[keyIds.size()];
            int i = 0;
            synchronized (keyIds) {
                for (Long id : keyIds) {
                    a[i] = id;
                    i++;
                }
            }

            b.putLongArray(EXTRA_SELECTED_KEY_IDS, a);

            b.putInt(EXTRA_PADDER_POS, mSpPaddingSym.getSelectedItemPosition());
        } catch (Exception ex) {
            Ln.e(ex, "error saving state!");
        }
    }
}
