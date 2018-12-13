package io.oversec.one.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Ordinary {@link EditTextPreference} which extends {@link ValidatedDialogPreference}.<br>
 * <br>
 * <i>Depends on</i>: {@link ValidatedDialogPreference}
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class ValidatedEditTextPreference extends ValidatedDialogPreference {
	
	// PRIVATE ====================================================================================
	
	/** Edit text view which contains input. */
	private EditText mEditText;
	
	/** Layout which contains edit text view and placed as margin layout into dialog. */
	private LinearLayout mEditTextlayout;
	
	/** Value of preference. */
	private String mText;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Get {@link EditText} containing input of preference.
	 * 
	 * @return {@link EditText} containing input of preference
	 */
	public EditText getEditText() {
		return mEditText;
	}
	
	/**
	 * Saves text to {@link SharedPreferences}.
	 * 
	 * @param text
	 *            text to save
	 */
	public void setText(String text) {
		if (text == null) {
			text = "";
		}
		
		final boolean wasBlocking = shouldDisableDependents();
		
		mText = text;
		
		persistString(text);
		
		final boolean isBlocking = shouldDisableDependents();
		
		if (isBlocking != wasBlocking) {
			notifyDependencyChange(isBlocking);
		}
	}
	
	/**
	 * Gets current set text of preference.<br>
	 * <br>
	 * This might be the given default value for this preference ({@code null} when none given) or
	 * the persisted {@link SharedPreferences} value.
	 * 
	 * @return current preference value
	 */
	public String getText() {
		return mText;
	}
	
	// OVERRIDDEN =================================================================================
	
	public ValidatedEditTextPreference(Context context) {
		super(context, null);
		initialize(null);
	}
	
	public ValidatedEditTextPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(attrs);
	}
	
	public ValidatedEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize(attrs);
	}
	
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Add path auto complete view to dialog.
	 */
	@Override
	protected View onCreateDialogView() {
		ViewParent parent = mEditTextlayout.getParent();
		
		if (parent != null) {
			((ViewGroup) parent).removeView(mEditTextlayout);
		}
		
		return mEditTextlayout;
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Set edit text value on bind.
	 */
	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		
		mEditText.setText(getText());
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Show keyboard on dialog show.
	 */
	@Override
	protected void showDialog(Bundle bundle) {
		super.showDialog(bundle);
		
		// display soft keyboard on show
		getDialog().getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}
	
	/**
 	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Change dependents when entered text is empty.
 	 */
	@Override
	public boolean shouldDisableDependents() {
		return TextUtils.isEmpty(mText) || super.shouldDisableDependents();
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Get default value.
	 */
	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getString(index);
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Set initial value.
	 */
	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setText(restoreValue ? getPersistedString(null) : (String) defaultValue);
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Save state.
	 */
	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		
		if (isPersistent() || mText == null) {
			return superState;
		}
		
		final SavedState myState = new SavedState(superState);
		myState.text = getText();
		return myState;
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Restore state.
	 */
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			super.onRestoreInstanceState(state);
			return;
		}
		
		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setText(myState.text);
	}
	
	/**
	 * <i>Overridden for internal use!</i><br>
	 * <br>
	 * Persist value (if desired).
	 */
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);
		
		if (positiveResult) {
			String result = mEditText.getText().toString();
			
			if (callChangeListener(result)) {
				setText(result);
			}
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void initialize(AttributeSet attrs) {
		// setup edit text
		mEditText = new EditText(getContext(), attrs);
		mEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
		mEditText.setEnabled(true);
		mEditText.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
		
		// setup layout for edit text
		int dip = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
				getContext().getResources().getDisplayMetrics()) + 0.5f);
		
		mEditTextlayout = new LinearLayout(getContext());
		mEditTextlayout.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
		mEditTextlayout.setPadding(dip, dip, dip, dip);
		mEditTextlayout.addView(mEditText);
	}
	
	
	private static class SavedState extends BaseSavedState {
		
		String text;
		
		public SavedState(Parcel source) {
			super(source);
			text = source.readString();
		}
		
		public SavedState(Parcelable superState) {
			super(superState);
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(text);
		}
		
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<SavedState> CREATOR =
				new Parcelable.Creator<SavedState>() {
					@Override
					public SavedState createFromParcel(Parcel in) {
						return new SavedState(in);
					}
					
					@Override
					public SavedState [] newArray(int size) {
						return new SavedState [size];
					}
				};
	}
}