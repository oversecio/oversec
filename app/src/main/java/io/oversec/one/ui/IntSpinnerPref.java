package io.oversec.one.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import io.oversec.one.R;

public abstract class IntSpinnerPref extends LinearLayout {
    public IntSpinnerPref(Context context, int titleResId, int subResId, Integer[] items) {
        super(context);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.intspinner_pref, this);

        TextView title = (TextView) findViewById(R.id.pref_title);
        TextView sub = (TextView) findViewById(R.id.pref_sub);
        title.setText(titleResId);
        sub.setText(subResId);

        final Spinner sp = (Spinner) findViewById(R.id.pref_spinner);

        final ArrayAdapter<Integer> adapter = new ArrayAdapter<>(context, R.layout.intspinner_item, items);
        sp.setAdapter(adapter);

        sp.setSelection(adapter.getPosition(getValue()));

        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setValue(adapter.getItem(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setValue(0);
            }
        });
    }

    abstract int getValue();

    abstract void setValue(int v);
}
