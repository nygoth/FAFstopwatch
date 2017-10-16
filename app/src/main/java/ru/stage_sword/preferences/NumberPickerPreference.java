package ru.stage_sword.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

import ru.stage_sword.fafstopwatch.R;

/**
 * A {@link android.preference.Preference} that displays a number NumberPicker as a dialog.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class NumberPickerPreference extends DialogPreference {

    public static final int DEFAULT_MAX_VALUE = 100;
    public static final int DEFAULT_MIN_VALUE = 0;
    public static final boolean DEFAULT_WRAP_SELECTOR_WHEEL = true;

    private final int mMinValue;
    private final int mMaxValue;
    private final boolean mWrapSelectorWheel;
    private int mActualValue;

    private NumberPicker mNumberPicker;
    private CharSequence mSummary;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference);
        mMinValue = a.getInteger(R.styleable.NumberPickerPreference_minValue, DEFAULT_MIN_VALUE);
        mMaxValue = a.getInteger(R.styleable.NumberPickerPreference_maxValue, DEFAULT_MAX_VALUE);
        mWrapSelectorWheel = a.getBoolean(R.styleable.NumberPickerPreference_wrapSelectorWheel, DEFAULT_WRAP_SELECTOR_WHEEL);
        a.recycle();
    }

    @Override
    protected View onCreateDialogView() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;

        mNumberPicker = new NumberPicker(getContext());
        mNumberPicker.setLayoutParams(layoutParams);

        FrameLayout dialogView = new FrameLayout(getContext());
        dialogView.addView(mNumberPicker);

        return dialogView;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mNumberPicker.setMinValue(mMinValue);
        mNumberPicker.setMaxValue(mMaxValue);
        mNumberPicker.setWrapSelectorWheel(mWrapSelectorWheel);
        mNumberPicker.setValue(getValue());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            mNumberPicker.clearFocus();
            int newValue = mNumberPicker.getValue();
            if (callChangeListener(newValue)) {
                setValue(newValue);
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, mMinValue);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        int value = restorePersistedValue ?
                getPersistedInt(mMinValue) :
                defaultValue != null ? (Integer) defaultValue : mMinValue;

        setValue(value);
    }

    public void setValue(int value) {
        this.mActualValue = value;
        persistInt(this.mActualValue);
    }

    public int getValue() {
        return this.mActualValue;
    }

    @Override
    public CharSequence getSummary() {
        if (mSummary == null) {
            mSummary = super.getSummary();
        }

        return mSummary != null ? String.format((String)mSummary, mActualValue) : String.valueOf(mActualValue);
    }
}
