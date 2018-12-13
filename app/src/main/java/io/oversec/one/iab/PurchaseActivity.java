package io.oversec.one.iab;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import io.oversec.one.R;

public class PurchaseActivity extends AppCompatActivity implements IabHelper.OnIabPurchaseFinishedListener {


    private static final int RQ_PURCHASE = 777;
    private IabUtil mIabUtil;
    private String[] mActiveFullVersionSkus;
    private Inventory mInventory;
    private RecyclerView mListView;

    public static void showForResult(Fragment f, int requestCode) {
        final Intent intent = new Intent(f.getActivity(), PurchaseActivity.class);
        f.startActivityForResult(intent,
                requestCode);
    }

    public static void showForResult(Activity a, int requestCode) {
        final Intent intent = new Intent(a, PurchaseActivity.class);
        a.startActivityForResult(intent,
                requestCode);
    }

    public static void show(Context ctx) {
        final Intent intent = new Intent(ctx, PurchaseActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase);


        ViewGroup vgIabOk = (ViewGroup) findViewById(R.id.iab_ok);
        ViewGroup vgIabNotAvailable = (ViewGroup) findViewById(R.id.iab_not_available);

        mIabUtil = IabUtil.getInstance(this);

        if (mIabUtil.isIabAvailable()) {
            vgIabNotAvailable.setVisibility(View.GONE);
            mIabUtil.checkFullVersionAndLoadSkuDetails(new FullVersionListener() {
                @Override
                public void onFullVersion_MAIN_THREAD(boolean isFullVersion) {
                    mInventory = mIabUtil.getInventory();
                    if (mInventory == null) {
                        setResult(RESULT_CANCELED);
                        finish();
                        return;
                    }

                    if (isFullVersion) {
                        setResult(RESULT_OK);
                        finish();
                        return;
                    }

                    mActiveFullVersionSkus = mIabUtil.getActiveFullVersionSkusWithoutPromo();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mListView = (RecyclerView) findViewById(R.id.list);

                            mListView.setHasFixedSize(true);

                            mListView.setLayoutManager(new LinearLayoutManager(PurchaseActivity.this));
                            mListView.setAdapter(new MyAdapter());
                        }
                    });

                }
            });
        } else {
            //Note: This should never happen: If play store is not available, the app
            //will now be unrestricted and show a "donations" page in the "about" section
            vgIabOk.setVisibility(View.GONE);

        }

    }


    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {


        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        public class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView mTv1;
            private final Button mBt;
            private String sku;


            public ViewHolder(ViewGroup v) {
                super(v);
                mTv1 = (TextView) v.findViewById(R.id.tv1);
                mBt = (Button) v.findViewById(R.id.btn);


                mBt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPurchaseFlow(sku);
                    }
                });
            }

            public void setSku(String sku) {
                this.sku = sku;
            }
        }

        public MyAdapter() {

        }

        // Create new views (invoked by the layout manager)
        @Override
        public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {


            // create a new view
            ViewGroup v = (ViewGroup) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_iab_sku, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

            String sku = mActiveFullVersionSkus[position];
            SkuDetails skud = mInventory.getSkuDetails(sku);

            holder.setSku(sku);
            holder.mTv1.setText(skud.getTitle());
            holder.mBt.setText(skud.getPrice());
        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return mActiveFullVersionSkus.length;
        }
    }

    private void startPurchaseFlow(String sku) {
        mIabUtil.launchPurchaseFlow(PurchaseActivity.this, sku, IabHelper.ITEM_TYPE_INAPP, RQ_PURCHASE, PurchaseActivity.this);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mIabUtil.handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onIabPurchaseFinished(IabResult result, Purchase info) {
        mIabUtil.onPurchaseFinished(result, info);
        if (result.isSuccess()) {
            setResult(RESULT_OK);
            finish();
        }
    }
}
