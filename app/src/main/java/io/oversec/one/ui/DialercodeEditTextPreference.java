package io.oversec.one.ui;

import android.content.Context;
import android.util.AttributeSet;

import io.oversec.one.R;


import static io.oversec.one.ui.MainSettingsFragment.MIN_SECRETCODE_LENGTH;

public class DialercodeEditTextPreference extends ValidatedEditTextPreference {

    public DialercodeEditTextPreference(Context context) {
        super(context);
    }

    public DialercodeEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DialercodeEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean validateInput(ValidatedDialogPreference pref) {

        boolean res = getEditText().getText().toString().trim().length() >= MIN_SECRETCODE_LENGTH;
        if (!res) {
            getEditText().setError(getContext().getString(R.string.secretdialer_code_too_short, "" + MIN_SECRETCODE_LENGTH));
        }
        return res;
    }
}
