package io.oversec.one.ui;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.AppsReceiver;
import io.oversec.one.crypto.Help;
import io.oversec.one.crypto.ui.WithHelp;
import io.oversec.one.db.Db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppsFragment extends Fragment implements WithHelp, AppsReceiver.IAppsReceiverListener {

    private static final int RQ_APP_CONFIG = 1;

    public static final String EXTRA_POSITION = "position";
    private RecyclerView mListView;
    private List<ApplicationInfo> mPackages;
    private RecyclerView.Adapter mAdapter;
    private Db mDb;
    private PackageManager mPackageManager;
    private LinearLayoutManager mLayoutManager;
    private boolean mPackageChangeReceiverRegistered;

    public void reload(int pos) {
        mAdapter.notifyItemChanged(pos);
    }

    public void reload() {
        //TODO: When reloading after app config activity should restore current scroll positionm or only refresh item/view for that app

        mPackages = getPackages();
        Collections.sort(mPackages, new Comparator<ApplicationInfo>() {

            @Override
            public int compare(ApplicationInfo lhs, ApplicationInfo rhs) {
                String s1 = mPackageManager.getApplicationLabel(lhs).toString();
                String s2 = mPackageManager.getApplicationLabel(rhs).toString();
                return s1.compareTo(s2);
            }
        });
        mAdapter = new MyAdapter();
        mListView.setAdapter(mAdapter);

    }

    @Override
    public void onAppChanged(Context ctx, String action, String packagename) {
        reload();
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {


        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        public class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView mTv1, mTv2;
            private final ImageView mIv1;
            private final CheckBox mCb1;
            private final MyOnClickListener mCl;
            private final ImageButton mBtHelp;

            private String packageName;

            public ViewHolder(ViewGroup v) {
                super(v);
                mTv1 = (TextView) v.findViewById(R.id.tv1);
                mTv2 = (TextView) v.findViewById(R.id.tv2);
                mIv1 = (ImageView) v.findViewById(R.id.iv1);
                mCb1 = (CheckBox) v.findViewById(R.id.cb1);
                mBtHelp = (ImageButton) v.findViewById(R.id.btn_help);

                mCl = new MyOnClickListener(this);
                v.setOnClickListener(mCl);

                mCb1.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mDb.setAppEnabled(packageName, mCb1.isChecked());
                    }
                });


                mBtHelp.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Help.INSTANCE.openForPackage(getActivity(), packageName);
                    }
                });
            }

            public void setPackageName(String packageName) {
                this.packageName = packageName;
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
                    .inflate(R.layout.listitem_app, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

            ApplicationInfo ai = mPackages.get(position);


            try {
                holder.mIv1.setImageDrawable((mPackageManager
                        .getApplicationIcon(ai)));
            } catch (Exception e) {
                holder.mIv1.setImageResource(android.R.drawable.ic_menu_edit);
            }
            holder.mTv2.setText(ai.packageName);
            holder.mTv1.setText(mPackageManager.getApplicationLabel(ai));


            holder.setPackageName(ai.packageName);

            holder.mCb1.setChecked(mDb.isAppEnabled(
                    ai.packageName));

        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return mPackages.size();
        }
    }

    public class MyOnClickListener implements OnClickListener {


        private final MyAdapter.ViewHolder mViewHolder;

        public MyOnClickListener(MyAdapter.ViewHolder viewHolder) {
            mViewHolder = viewHolder;
        }

        @Override
        public void onClick(View v) {
            AppConfigActivity.showForResult(getActivity(), RQ_APP_CONFIG, mViewHolder.packageName);
        }


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RQ_APP_CONFIG) {
            reload();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mDb = Core.getInstance(container.getContext()).getDb();
        mPackageManager = container.getContext().getPackageManager();

        View view = inflater.inflate(R.layout.fragment_main_apps, container, false);

        mListView = (RecyclerView) view.findViewById(R.id.plist);

        mListView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(getActivity());
        mListView.setLayoutManager(mLayoutManager);


        reload();
        AppsReceiver.Companion.addListener(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        AppsReceiver.Companion.removeListener(this);
        super.onDestroyView();
    }


    @Override
    public void onResume() {
        super.onResume();
        reload();
    }


    private void checkAll(boolean b) {
        for (ApplicationInfo p : mPackages) {
            mDb.setAppEnabled(p.packageName, b);
        }
        mAdapter.notifyDataSetChanged();
    }


    private List<ApplicationInfo> getPackages() {


        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resInfos = mPackageManager.queryIntentActivities(intent, 0);
        //using hashset so that there will be no duplicate packages,
        //if no duplicate packages then there will be no duplicate apps
        HashSet<String> packageNames = new HashSet<>(0);
        List<ApplicationInfo> appInfos = new ArrayList<>(0);

        //getting package names and adding them to the hashset
        for (ResolveInfo resolveInfo : resInfos) {
            packageNames.add(resolveInfo.activityInfo.packageName);
        }

        //now we have unique packages in the hashset, so get their application infos
        //and add them to the arraylist
        Set<String> mIgnore = Core.getInstance(getActivity()).getDb().getIgnoredPackages();
        for (String packageName : packageNames) {

            if (!mIgnore.contains(packageName)) {
                try {
                    appInfos.add(mPackageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
                } catch (PackageManager.NameNotFoundException e) {
                    //Do Nothing
                }
            }
        }

        //to sort the list of apps by their names
        Collections.sort(appInfos, new ApplicationInfo.DisplayNameComparator(mPackageManager));
        return appInfos;
    }

    @Override
    public Help.ANCHOR getHelpAnchor() {
        return Help.ANCHOR.main_apps;
    }
}