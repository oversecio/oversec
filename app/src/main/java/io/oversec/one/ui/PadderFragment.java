package io.oversec.one.ui;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.oversec.one.common.CoreContract;
import io.oversec.one.crypto.Help;
import io.oversec.one.R;
import io.oversec.one.crypto.encoding.pad.PadderContent;
import io.oversec.one.crypto.ui.util.SimpleDividerItemDecoration;
import io.oversec.one.db.PadderDb;
import io.oversec.one.crypto.encoding.pad.XCoderAndPadderFactory;
import io.oversec.one.crypto.ui.WithHelp;

import java.util.List;


public class PadderFragment extends Fragment implements WithHelp {

    private static final int RQ_CREATE_NEW_CONTENT = 8008;
    private static final int RQ_SHOW_DETAILS = 8009;
    private static final int RQ_UPGRADE = 8010;
    private RecyclerView mRecyclerView;
    private PadderDb mDb;

    public PadderFragment() {
        // Required empty public constructor
    }

    public static PadderFragment newInstance() {
        PadderFragment fragment = new PadderFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        Context ctx = container.getContext();
        mDb = PadderDb.getInstance(ctx);

        View res = inflater.inflate(R.layout.fragment_padders, container, false);
        mRecyclerView = (RecyclerView) res.findViewById(R.id.list);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        mRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity()));


        FloatingActionButton fab = (FloatingActionButton) res.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              showAddNewPadderIfFullVersion();


            }
        });

        refreshList();
        return res;
    }

    void refreshList() {
        mRecyclerView.setAdapter(new PadderContentRecyclerViewAdapter(mDb.getAllValues()));
    }

    private void showAddNewPadderIfFullVersion() {
        CoreContract.Companion.getInstance().doIfFullVersionOrShowPurchaseDialog(PadderFragment.this,new Runnable() {
            @Override
            public void run() {
                PadderDetailActivity.showForResult(PadderFragment.this, RQ_CREATE_NEW_CONTENT,null);
            }
        }, RQ_UPGRADE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode==RQ_CREATE_NEW_CONTENT) {
            refreshList();
            XCoderAndPadderFactory.Companion.getInstance(getActivity()).reload();
        }
        else
        if (requestCode==RQ_SHOW_DETAILS) {
            refreshList();
            XCoderAndPadderFactory.Companion.getInstance(getActivity()).reload();
        }
        else
        if (requestCode== RQ_UPGRADE) {
            if (resultCode==AppConfigActivity.RESULT_OK) {
                showAddNewPadderIfFullVersion();
            }
        }
    }

    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.main_padders;
    }

    class PadderContentRecyclerViewAdapter extends RecyclerView.Adapter<PadderContentRecyclerViewAdapter.ViewHolder> {

        protected final List<PadderContent> mPadders;

        public PadderContentRecyclerViewAdapter(List<PadderContent> items) {
            mPadders = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_padding, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            PadderContent pc = mPadders.get(position);
            holder.mTvName.setText(pc.getName());
            holder.mTvSample.setText(pc.getContentBegin());
        }

        @Override
        public int getItemCount() {
            return mPadders.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mTvName, mTvSample;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mTvName = (TextView) view.findViewById(R.id.tv_title);
                mTvSample = (TextView) view.findViewById(R.id.tv_example);

                mView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PadderDetailActivity.showForResult(PadderFragment.this, RQ_SHOW_DETAILS,
                                mPadders.get(getAdapterPosition()).getKey());
                    }
                });
            }
        }
    }
}
