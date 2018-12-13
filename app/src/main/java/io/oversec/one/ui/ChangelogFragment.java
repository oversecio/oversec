package io.oversec.one.ui;


import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.oversec.one.R;

import org.markdown4j.Markdown4jProcessor;
import org.sufficientlysecure.htmltextview.HtmlAssetsImageGetter;
import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.io.IOException;


public class ChangelogFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_changelog, container, false);


        HtmlTextView aboutTextView = (HtmlTextView) v.findViewById(R.id.changelog_text);

        try {
            String html = new Markdown4jProcessor().process(
                    getActivity().getResources().openRawResource(R.raw.changelog));
            aboutTextView.setHtml(html, new HtmlAssetsImageGetter(container.getContext()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return v;
    }
}
