package io.oversec.one.ui;

import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import io.oversec.one.BuildConfig;
import io.oversec.one.R;

import static android.content.Context.CLIPBOARD_SERVICE;

public class DonationFragment extends Fragment {

    public DonationFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_donation, container, false);

        setButton(v, R.id.btn_btc, R.string.donate_btn_btc, BuildConfig.DONATION_BTC);
        setButton(v, R.id.btn_eth, R.string.donate_btn_eth, BuildConfig.DONATION_ETH);
        setButton(v, R.id.btn_iota, R.string.donate_btn_iota, BuildConfig.DONATION_IOTA);
        setButton(v, R.id.btn_dash, R.string.donate_btn_dash, BuildConfig.DONATION_DASH);

        return v;
    }

    private void setButton(final View v, int buttonId, int textId, final String address) {
        Button b = v.findViewById(buttonId);
        String coin = getString(textId);

        SpannableString spannable = new SpannableString(coin + "\n" + address);
        spannable.setSpan(
                new RelativeSizeSpan(1.5f),
                0, coin.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(
                new RelativeSizeSpan(0.85f),
                coin.length() + 1, spannable.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(spannable);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(getString(R.string.donate_clip_label), address);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(v.getContext(), R.string.donate_toast_copied, Toast.LENGTH_LONG).show();
            }
        });
    }
}
