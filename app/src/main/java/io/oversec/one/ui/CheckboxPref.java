package io.oversec.one.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.oversec.one.R;

public abstract class CheckboxPref extends LinearLayout {
    public CheckboxPref(Context context, int titleResId, int subResId) {
        this(context, context.getString(titleResId), context.getString(subResId));
    }

    public CheckboxPref(Context context, String head, String body) {
        super(context);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.checkbox_pref, this);

        TextView title = (TextView) findViewById(R.id.pref_title);
        TextView sub = (TextView) findViewById(R.id.pref_sub);
        title.setText(head);
        sub.setText(body);

        final CheckBox cb = (CheckBox) findViewById(R.id.pref_checkbox);
        cb.setChecked(getValue());

        cb.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                setValue(
                        cb.isChecked());

            }
        });
    }

    abstract boolean getValue();

    abstract void setValue(boolean b);
}
