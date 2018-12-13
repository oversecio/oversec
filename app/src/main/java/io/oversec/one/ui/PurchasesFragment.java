package io.oversec.one.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import io.oversec.one.R;
import io.oversec.one.iab.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PurchasesFragment extends Fragment {
    private Inventory mInventory;

    public PurchasesFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_purchases, container, false);

        final RecyclerView rv = (RecyclerView) v.findViewById(R.id.list);
        rv.setHasFixedSize(true);
        rv.setLayoutManager(new LinearLayoutManager(container.getContext()));

        final IabUtil aIabUtil = IabUtil.getInstance(container.getContext());
        aIabUtil.checkFullVersionAndLoadSkuDetails(new FullVersionListener() {
            @Override
            public void onFullVersion_MAIN_THREAD(boolean isFullVersion) {

                mInventory = aIabUtil.getInventory();
                if (mInventory!=null) {
                    container.post(new Runnable() {
                        @Override
                        public void run() {
                            rv.setAdapter(new MyAdapter(mInventory.getAllPurchases()));
                        }
                    });
                }
            }
        });

        return v;
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {


        private final List<Purchase> mPurchases;

        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        public class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView mTv1;

            public ViewHolder(ViewGroup v) {
                super(v);
                mTv1 = (TextView) v.findViewById(R.id.tv1);
            }
        }

        public MyAdapter(List<Purchase> purchases) {
            mPurchases = new ArrayList<>();

            //filter out cancelled or refunded ourchases
            for (Purchase p : purchases) {
                if (p.getPurchaseState() == 0) {
                    mPurchases.add(p);
                }
            }
        }

        // Create new views (invoked by the layout manager)
        @Override
        public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {
            // create a new view
            ViewGroup v = (ViewGroup) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_iab_purchase, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Purchase pu = mPurchases.get(position);
            String text = "N/A";
            if (pu != null) {
                String skuTitle = "";
                SkuDetails skuDetails = mInventory.getSkuDetails(pu.getSku());
                if (skuDetails != null) { //there seems to be a race / load condition, so fall back to raw sku string.
                    skuTitle = skuDetails.getTitle();
                }
                else {
                    skuTitle = pu.getSku();
                }
                text = DateFormat.getMediumDateFormat(getActivity()).format(new Date(pu.getPurchaseTime()))
                        + "   "
                        + skuTitle;
            }
            holder.mTv1.setText(text);
        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return mPurchases.size();
        }
    }
}
