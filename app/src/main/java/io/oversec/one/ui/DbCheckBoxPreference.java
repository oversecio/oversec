package io.oversec.one.ui;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.Preference;

public abstract class DbCheckBoxPreference extends CheckBoxPreference {
    public DbCheckBoxPreference(Context ctx, String title, String summary) {
        super(ctx);
        setTitle(title);
        setSummary(summary);
        setChecked(getValue());
        setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                setValue((boolean)newValue);
                return true;
            }
        });
    }

    protected abstract boolean getValue();
    protected abstract void setValue(boolean b);

}
