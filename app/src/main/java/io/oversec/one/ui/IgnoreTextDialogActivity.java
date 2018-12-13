package io.oversec.one.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.encoding.ZeroWidthXCoder;


public class IgnoreTextDialogActivity extends FragmentActivity {

    private static final String FRAGMENT_TAG = "dialog";
    private static final String EXTRA_ENCRYPTED_TEXT = "EXTRA_ENCRYPTED_TEXT";

    public static void show(Context ctx, String encodedText) {
        try {
            Intent i = new Intent();

            i.putExtra(EXTRA_ENCRYPTED_TEXT, encodedText);
            i.setClass(ctx, IgnoreTextDialogActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        IgnoreTextFragment frag = new IgnoreTextFragment();
        frag.setArguments(getIntent().getExtras());
        frag.show(getSupportFragmentManager(), FRAGMENT_TAG);
    }

    @Override
    protected void onPause() {
        super.onPause();

        DialogFragment dialog = (DialogFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Core.getInstance(this).onIgnoreTextDialogActivityClosed();
    }

    public static class IgnoreTextFragment extends DialogFragment {
        private FrameLayout mLayout;
        private TextView mTitle;

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            ContextThemeWrapper ctw = new ContextThemeWrapper(getContext(), R.style.AppTheme);

            AlertDialog.Builder alert = new AlertDialog.Builder(ctw);

            // No title, see http://www.google.com/design/spec/components/dialogs.html#dialogs-alerts
            //alert.setTitle()


            LayoutInflater inflater = LayoutInflater.from(ctw);
            mLayout = (FrameLayout) inflater.inflate(R.layout.ignore_text_dialog, null);
            alert.setView(mLayout);

            //  mTitle = (TextView) mLayout.findViewById(io.oversec.one.crypto.R.id.passphrase_text);
            //  mTitle.setText(getString(io.oversec.one.crypto.R.string.simplesym_add_password_title));

            final String encryptedText = getActivity().getIntent().getStringExtra(EXTRA_ENCRYPTED_TEXT);
            String stripped = ZeroWidthXCoder.Companion.stripInvisible(encryptedText);
            ((TextView) mLayout.findViewById(R.id.orig_text)).setText(stripped);

            alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();

                }
            });

            alert.setPositiveButton(R.string.action_ignore_text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    Core.getInstance(getActivity()).addIgnoredText(encryptedText);
                    dialog.dismiss();
                }
            });

            AlertDialog dialog = alert.create();
            return dialog;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            FragmentActivity a = getActivity();
            if (a != null) {
                a.setResult(RESULT_CANCELED);
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            FragmentActivity a = getActivity();
            if (a != null) {
                a.finish();
            }
        }
    }
}
