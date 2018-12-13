package io.oversec.one.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.oversec.one.BuildConfig;
import io.oversec.one.R;

import org.markdown4j.Markdown4jProcessor;
import org.sufficientlysecure.htmltextview.HtmlAssetsImageGetter;
import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.io.IOException;

public class AboutFragment extends Fragment {

    public AboutFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_about, container, false);

        //TODO use a format string
        ((TextView) v.findViewById(R.id.help_about_version)).setText(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") "
                + (BuildConfig.DEBUG ? " DEBUG" : "") + " [" + BuildConfig.FLAVOR + "]");

        HtmlTextView aboutTextView = (HtmlTextView) v.findViewById(R.id.about_text);

        try {
            String html = new Markdown4jProcessor().process(
                    getActivity().getResources().openRawResource(R.raw.about));


            aboutTextView.setHtml(html, new HtmlAssetsImageGetter(container.getContext()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return v;
    }
}
