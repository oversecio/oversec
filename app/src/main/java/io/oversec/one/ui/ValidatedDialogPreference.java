package io.oversec.one.ui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import io.oversec.one.R;


/**
 * Dialog preference which doesn't dismiss on invalid input.<br>
 * <br>
 * Extending class can override {@link #validateInput(ValidatedDialogPreference)} and you can set a
 * validation callback on activity by defining the method name in XML attribute {@code onValidate}
 * (method should have this signature {@code boolean methodName(}{@link ValidatedDialogPreference}
 * {@code )}).<br>
 * <br>
 * If intern validation returns {@code true} then the defined XML validation method will be called.
 * If one of them return {@code false} then dialog won't be closed. You can also return
 * {@code false} and perform a long running task which calls {@link #getDialog()}{@code .dismiss()}
 * when validation was successful. It's also possible to modify the final value by the given
 * preference argument.<br>
 * <br>
 * If you don't use the given validation options this class behaves like a ordinary
 * {@link DialogPreference}.<br>
 * <br>
 * <i>Depends on</i>: {@code res/values/attrs.xml}
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public abstract class ValidatedDialogPreference extends DialogPreference {
	
	// PRIVATE ====================================================================================
	
	/** Reflected method for validation callback. */
	private Method mValidationCallback;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Set callback on activity.<br>
	 * <br>
	 * Method should have this signature {@code boolean methodName(}
	 * {@link ValidatedDialogPreference}{@code )}.
	 * 
	 * @param methodName
	 *            method name of callback
	 */
	public void setOnValidation(String methodName) {
		setOnValidation(getContext(), methodName);
	}
	
	/**
	 * Set callback on any object.<br>
	 * <br>
	 * Method should have this signature {@code boolean methodName(}
	 * {@link ValidatedDialogPreference}{@code )}.
	 * 
	 * @param object
	 *            object on which callback method will be called
	 * @param methodName
	 *            method name of callback
	 */
	public void setOnValidation(Object object, String methodName) {
		try {
			mValidationCallback = object.getClass().getDeclaredMethod(
					methodName, ValidatedDialogPreference.class);
			
			if (!mValidationCallback.getReturnType().equals(boolean.class)) {
				throw new IllegalArgumentException("Method " + methodName + "("
						+ ValidatedDialogPreference.class.getSimpleName()
						+ ") should return a boolean");
			}
			
			mValidationCallback.setAccessible(true);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("Method " + methodName + "("
					+ ValidatedDialogPreference.class.getSimpleName()
					+ ") doesn't exist in activity", e);
		}
	}
	
	// ABSTRACT ===================================================================================
	
	/**
	 * Override this method to validate input.<br>
	 * <br>
	 * Default implementation returns {@code true}.
	 * 
	 * @return {@code true} if input is correct and preference dialog should be closed
	 * 
	 * @see ValidatedDialogPreference
	 */
	protected boolean validateInput(ValidatedDialogPreference pref) {
		return true;
	}
	
	// OVERRIDDEN =================================================================================
	
	public ValidatedDialogPreference(Context context) {
		super(context, null);
		initialize(null);
	}
	
	public ValidatedDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(attrs);
	}
	
	public ValidatedDialogPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize(attrs);
	}
	
	
	/**
	 * <i>Overridden for interlan use!</i><br>
	 * <br>
	 * Avoid close of dialog and perform validation instead.
	 */
	@Override
	protected void showDialog(Bundle state) {
		super.showDialog(state);
		
		Button button = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					ValidatedDialogPreference.this.onClick(null, AlertDialog.BUTTON_POSITIVE);
					
					if (validateInput(ValidatedDialogPreference.this)
							&& (mValidationCallback == null || (Boolean) mValidationCallback
									.invoke(getContext(), ValidatedDialogPreference.this))) {
						getDialog().dismiss();
					}
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
	
	// PRIVATE ====================================================================================
	
	private void initialize(AttributeSet attrs) {
		if (attrs == null) {
			return;
		}
		
//		TypedArray a = getContext().obtainStyledAttributes(
//				attrs, R.styleable.ValidatedDialogPreference);
//
//		String callback = a.getString(R.styleable.ValidatedDialogPreference_onValidate);
//
//		if (callback != null) {
//			setOnValidation(callback);
//		}
//
//		a.recycle();
	}
}