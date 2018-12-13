package io.oversec.one.ui;

import android.app.Activity;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import io.oversec.one.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.oversec.one.Core;
import io.oversec.one.Util;
import io.oversec.one.crypto.ui.util.SimpleDividerItemDecoration;
import io.oversec.one.db.Db;

public class PostAcsEnableActivity extends Activity {
    private static final String EXTRA_TARGET_APP_NOT_INSTALLED = "tani";
    private RecyclerView mRvApps;

    public static void show(Context ctx) {
        boolean targetAppStarted = false;
        boolean targetAppNotInstalled = false;

        Core.getInstance(ctx).setInitiallyDisabled(true);

        if (!Util.isOversec(ctx)) {
            //start target app, but do NOT yet process it, otherwise we'll get oversec overlays on top of this app

            String packagename = ctx.getResources().getString(R.string.feature_package);
            PackageManager pm = ctx.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packagename);
            if (intent != null) {
                targetAppStarted = true;
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } else {
                targetAppNotInstalled = true;
            }
        }

        Intent i = new Intent();
        i.setClass(ctx, PostAcsEnableActivity.class);
        if (targetAppStarted) {
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (targetAppNotInstalled) {
            i.putExtra(EXTRA_TARGET_APP_NOT_INSTALLED, true);
        }
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_acs_enable);

        Button btConfigureApps = (Button) findViewById(R.id.btn_configure_apps);
        btConfigureApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBackToMainApp();
            }
        });


        mRvApps = (RecyclerView) findViewById(R.id.rv_apps);
        mRvApps.setLayoutManager(new LinearLayoutManager(this));
        mRvApps.addItemDecoration(new SimpleDividerItemDecoration(this));
        mRvApps.setAdapter(new MyAdapter());

        if (!Util.isOversec(this)) {
            mRvApps.setVisibility(View.GONE);


            TextView tv2 = (TextView) findViewById(R.id.text2);
            if (getIntent().getBooleanExtra(EXTRA_TARGET_APP_NOT_INSTALLED, false)) {
                tv2.setText(getString(R.string.settings_acs_post_enable_msg_appsec_target_package_not_installed, getResources().getString(R.string.feature_package)));
            } else {
                tv2.setText(getString(R.string.settings_acs_post_enable_msg_appsec, Util.getPackageLabel(this, getResources().getString(R.string.feature_package))));
            }

            btConfigureApps.setText(R.string.action_ok);
            btConfigureApps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }


    }

    private void goBackToMainApp() {
        finish();
        MainActivity.showApps(this);
    }

    @Override
    protected void onStop() {
        //signal the ACS that'we ready to go
        Core.getInstance(this).setInitiallyDisabled(false);
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        finish(); //-> this should bring us back to the Main activity
    }

    private class MyOnClickListener implements View.OnClickListener {


        private final MyAdapter.ViewHolder mViewHolder;

        public MyOnClickListener(MyAdapter.ViewHolder viewHolder) {
            mViewHolder = viewHolder;
        }

        @Override
        public void onClick(View v) {
            finish();
            MainActivity.showApps(PostAcsEnableActivity.this); //this will clear the stack with the settings activities

            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(mViewHolder.packageName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

        }


    }


    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {
        private List<ApplicationInfo> mPackages;

        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        public class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView mTv1;
            private final ImageView mIv1;
            private final MyOnClickListener mCl;


            private String packageName;

            public ViewHolder(ViewGroup v) {
                super(v);
                mTv1 = (TextView) v.findViewById(R.id.tv1);
                mIv1 = (ImageView) v.findViewById(R.id.iv1);

                mCl = new MyOnClickListener(this);
                v.setOnClickListener(mCl);


            }

            public void setPackageName(String packageName) {
                this.packageName = packageName;
            }
        }

        public MyAdapter() {
            mPackages = getEnabledPackages();
        }

        // Create new views (invoked by the layout manager)
        @Override
        public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {

            // create a new view
            ViewGroup v = (ViewGroup) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_app_only, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

            ApplicationInfo ai = mPackages.get(position);


            try {
                holder.mIv1.setImageDrawable((getPackageManager()
                        .getApplicationIcon(ai)));
            } catch (Exception e) {
                holder.mIv1.setImageResource(android.R.drawable.ic_menu_edit);
            }
            holder.mTv1.setText(getPackageManager().getApplicationLabel(ai));


            holder.setPackageName(ai.packageName);


        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return mPackages.size();
        }
    }

    private List<ApplicationInfo> getEnabledPackages() {
        Db db = Core.getInstance(this).getDb();

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resInfos = getPackageManager().queryIntentActivities(intent, 0);
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

        for (String packageName : packageNames) {

            if (db.isAppEnabled(packageName)) {
                try {
                    appInfos.add(getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA));
                } catch (PackageManager.NameNotFoundException e) {
                    //Do Nothing
                }
            }
        }

        //to sort the list of apps by their names
        Collections.sort(appInfos, new ApplicationInfo.DisplayNameComparator(getPackageManager()));
        return appInfos;
    }
}
