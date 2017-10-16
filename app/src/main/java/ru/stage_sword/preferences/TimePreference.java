package ru.stage_sword.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import ru.stage_sword.fafstopwatch.R;

import static ru.stage_sword.preferences.NumberPickerPreference.DEFAULT_MIN_VALUE;

/**
 * Created by nygoth on 15.10.2017.
 * Time TimePicker preference. For setting time intervals in millis by common dialog
 */

@SuppressWarnings({"unused", "WeakerAccess"})
@SuppressLint("DefaultLocale")
public class TimePreference extends DialogPreference {
    private static final int DEFAULT_VALUE = 60; // Default value for preference -- one minute
    private static final int DEFAULT_MAX_VALUE = 10;
    private final int mMinValue;
    private final int mMaxValue;

    private CharSequence mSummary;
    private TextView mCurrentValueView;
    private NumberPicker mMinutesSelector;
    private NumberPicker mSecondsSelector;
    private int mCurrentValue = DEFAULT_VALUE;

    private NumberPicker.OnValueChangeListener mTimeChangeListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker numberPicker, int oldValue, int newValue) {
            int minutes = mMinutesSelector.getValue();
            int seconds = mSecondsSelector.getValue();

            if(numberPicker == mMinutesSelector)
                minutes = newValue;
            else
                seconds = newValue;

            mCurrentValueView.setText(String.format("%02d:%02d", minutes, seconds));
        }
    };

    public TimePreference(Context context) {
        this(context, null);
    }

    public TimePreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public TimePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setDialogLayoutResource(R.layout.time_picker_layout);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TimePreference);
        mMinValue = a.getInteger(R.styleable.TimePreference_minMinutes, DEFAULT_MIN_VALUE);
        mMaxValue = a.getInteger(R.styleable.TimePreference_maxMinutes, DEFAULT_MAX_VALUE);
        a.recycle();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mCurrentValueView = view.findViewById(R.id.current_value);
        mMinutesSelector = view.findViewById(R.id.minutes_picker);
        mSecondsSelector = view.findViewById(R.id.seconds_picker);

        mMinutesSelector.setMinValue(mMinValue);
        mMinutesSelector.setMaxValue(mMaxValue);
        mSecondsSelector.setMinValue(0);
        mSecondsSelector.setMaxValue(59);

        mMinutesSelector.setWrapSelectorWheel(false);
        mSecondsSelector.setWrapSelectorWheel(true);

        mMinutesSelector.setValue(mCurrentValue / 60);
        mSecondsSelector.setValue(mCurrentValue % 60);

        mMinutesSelector.setOnValueChangedListener(mTimeChangeListener);
        mSecondsSelector.setOnValueChangedListener(mTimeChangeListener);

        mCurrentValueView.setText(String.format("%02d:%02d", mCurrentValue / 60, mCurrentValue % 60));
//  //        String[] minuteValues = new String[12];
//
//        for (int i = 0; i < minuteValues.length; i++) {
//            String number = Integer.toString(i*5);
//            minuteValues[i] = number.length() < 2 ? "0" + number : number;
//        }
//
//        mSecondsSelector.setDisplayedValues(minuteValues);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            mCurrentValue = mMinutesSelector.getValue() * 60 + mSecondsSelector.getValue();

            setSummary(getSummary());
            if (callChangeListener(mCurrentValue)) {
                persistInt(mCurrentValue);
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        mCurrentValue = DEFAULT_VALUE;

        if (restoreValue)
            mCurrentValue = getPersistedInt(mCurrentValue);
        else if (defaultValue != null)
            mCurrentValue = Integer.valueOf((String) defaultValue);

        if (mMinutesSelector != null)
            mMinutesSelector.setValue(mCurrentValue / 60);

        if (mSecondsSelector != null)
            mSecondsSelector.setValue(mCurrentValue % 60);
//        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        if (mSummary == null) {
            mSummary = super.getSummary();
            mSummary = mSummary != null ? "\n\n" + mSummary : "";
        }

        return String.format("%02d:%02d%s", (mCurrentValue / 60), (mCurrentValue % 60), mSummary);
    }
}
