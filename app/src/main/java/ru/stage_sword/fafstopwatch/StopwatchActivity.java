package ru.stage_sword.fafstopwatch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.annotation.IdRes;
import android.support.annotation.StringRes;
import android.support.transition.Scene;
import android.support.transition.TransitionInflater;
import android.support.transition.TransitionManager;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import ru.stage_sword.preferences.StopwatchPreferences;

import static ru.stage_sword.fafstopwatch.PrecisionChronometer.DELAYED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.ON_OFF;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.ON_OFF_DELAYED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.UNKNOWN_STATE;

public class StopwatchActivity extends AppCompatActivity
        implements  View.OnClickListener,
                    View.OnLongClickListener,
                    PrecisionChronometer.OnChronometerHoldListener {
    @SuppressWarnings("unused")
    private static final String TAG = "FAF";

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 200;

    private static final String UI_VISIBLE_STATE    = "uiVisible";
    private static final String CURRENT_VIEW        = "mCurrentView";
    private static final String SPECIAL_TIMER_STATE = "specialTimerState";

    /*
     * Индексы значений времени в массивах
     */
    private static final int SOLO    = R.id.radio_Solo;
    private static final int SYNCHRO = R.id.radio_Synchro;
    private static final int DUET    = R.id.radio_Duet;
    private static final int GROUP   = R.id.radio_Group;

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mTouchpad.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final RadioGroup.OnCheckedChangeListener mDisciplineSelector = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
            if (i == -1) {
                findViewById(R.id.total_overrun_title).setVisibility(View.GONE);
                findViewById(R.id.special_underrun_title).setVisibility(View.GONE);
                return;
            }

            long elapsed, total;

            // Общее время должно быть не больше
            if (mTotalTimerView != null) {
                elapsed = mTotalTimerView.getTimeElapsed() / 10;
                total = mDisciplineTotalDuration.get(i);
                setTimeComment((int) ((elapsed - total) * 10),
                        (TextView)findViewById(R.id.total_overrun_title), R.string.overrun_title);
            }

            // А фехтовального времени должно быть не меньше
            if (mSpecialTimerView != null) {
                elapsed = mSpecialTimerView.getTimeElapsed() / 10;
                total = mDisciplineFencingDuration.get(i);
                setTimeComment((int) ((total - elapsed) * 10),
                        (TextView)findViewById(R.id.special_underrun_title), R.string.underrun_title);
            }
        }

        private void setTimeComment(int time, TextView widget, @StringRes int stringId) {
            if (time > 0) {
                String msg = getResources().getString(stringId);
                widget.setText(String.format(msg, PrecisionChronometer.getHumanReadableTime(time)));
                widget.setVisibility(View.VISIBLE);
            } else {
                widget.setVisibility(View.GONE);
            }
        }
    };

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
//    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {
//        @Override
//        public boolean onTouch(View view, MotionEvent motionEvent) {
////            if (AUTO_HIDE) {
////                delayedHide(AUTO_HIDE_DELAY_MILLIS);
////            }
//            return false;
//        }
//    };

    private PrecisionChronometer mSpecialTimerView;
    private final Handler mHideHandler = new Handler();
    private PrecisionChronometer mTotalTimerView;
    private Scene mTotalTimerScene;
    private Scene mSpecialTimerScene;
    private Scene mResultsScene;
    private View mTouchpad;
    private TransitionManager mTransitionManager;
    private int mAnimationDuration;
    private View mSpecialTimerBackgroundView;
    private static int mCurrentView = R.layout.total_timer_scene;
    private int mSpecialTimerStopDelay;
    private boolean mNotifyVibrate;
    private ArrayMap<Integer, Integer> mDisciplineTotalDuration = new ArrayMap<>();
    private ArrayMap<Integer, Integer> mDisciplineFencingDuration = new ArrayMap<>();

    //    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//    }
//
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(CURRENT_VIEW, mCurrentView);

        if(mCurrentView != R.layout.total_timer_scene) {
            mNotifyVibrate = false;

            outState.putBoolean(UI_VISIBLE_STATE,    mVisible);
            outState.putInt    (SPECIAL_TIMER_STATE, mSpecialTimerView.getState());
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_stopwatch);

        mNotifyVibrate = true;
        mVisible = true;
        if (savedInstanceState != null) {
            mVisible     = savedInstanceState.getBoolean(UI_VISIBLE_STATE, true);
            mCurrentView = savedInstanceState.getInt    (CURRENT_VIEW,     R.layout.total_timer_scene);
        }

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        ViewGroup sceneRoot = findViewById(R.id.timer_scene_root);

        mTotalTimerScene   = Scene.getSceneForLayout(sceneRoot, R.layout.total_timer_scene,   this);
        mSpecialTimerScene = Scene.getSceneForLayout(sceneRoot, R.layout.special_timer_scene, this);
        mResultsScene      = Scene.getSceneForLayout(sceneRoot, R.layout.final_results_scene, this);

        LayoutInflater.from(this).inflate(mCurrentView, sceneRoot);

        TransitionInflater inflater = TransitionInflater.from(this);
        mTransitionManager = inflater.inflateTransitionManager(R.transition.chronometer_transitions, sceneRoot);

        mAnimationDuration = getResources().getInteger(R.integer.timer_animation_duration);

        /*
         * Величины, определяющие временные нормативы конкретных дисциплин
         */
        // Интервал, который идёт в зачёт фехтовальной практики после её окончания
        mSpecialTimerStopDelay = getResources().getInteger(R.integer.special_timer_default_stop_delay);

        // Общая продолжительность выступления в соответствующих дисциплинах. В миллисекундах
        mDisciplineTotalDuration.put(SOLO,    getResources().getInteger(R.integer.solo_discipline_total_duration));
        mDisciplineTotalDuration.put(GROUP,   getResources().getInteger(R.integer.group_discipline_total_duration));
        mDisciplineTotalDuration.put(DUET,    getResources().getInteger(R.integer.duet_discipline_total_duration));
        mDisciplineTotalDuration.put(SYNCHRO, getResources().getInteger(R.integer.synchro_discipline_total_duration));

        mDisciplineFencingDuration.put(SOLO,    getResources().getInteger(R.integer.solo_discipline_fencing_duration));
        mDisciplineFencingDuration.put(GROUP,   getResources().getInteger(R.integer.group_discipline_fencing_duration));
        mDisciplineFencingDuration.put(DUET,    getResources().getInteger(R.integer.duet_discipline_fencing_duration));
        mDisciplineFencingDuration.put(SYNCHRO, getResources().getInteger(R.integer.synchro_discipline_fencing_duration));

        mTouchpad = findViewById(R.id.touchpad);
        if(mCurrentView == R.layout.final_results_scene)
            ((RadioGroup) findViewById(R.id.discipline_selector)).setOnCheckedChangeListener(mDisciplineSelector);

        actualizeUIVisibility();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        int specialTimerState = UNKNOWN_STATE;
        if (savedInstanceState != null) {
            specialTimerState = savedInstanceState.getInt(SPECIAL_TIMER_STATE, UNKNOWN_STATE);
        }

        setTouchpadListeners();

        TotalTimerSetup();

        switch (mCurrentView) {
            case R.layout.special_timer_scene:
                SpecialTimerSetup();
                if (specialTimerState == DELAYED) { // Изменим цвет фона для соответствующего состояния
                    TransitionDrawable trans = (TransitionDrawable) mSpecialTimerBackgroundView.getBackground();
                    trans.resetTransition();
                    trans.startTransition(0);
                }

                break;
            case R.layout.final_results_scene:
                mSpecialTimerView = findViewById(R.id.special_timer_view);
                removeTouchpadListeners();

                // Смысл этого кода в том, что слушатель mDisciplineSelector устанавливается раньше, чем
                // актуализируются секундомеры. Поэтому, когда вызывается onCheckedChanged() (при восстановлении состояния)
                // мы ещё не имеем даже инициализированных mTotalTimerView и mSpecialTimerView
                // Поэтому тут его нужно вызвать ещё раз.
                // Если слушатель устанавливать иначе, чем сделано сейчас, то он либо:
                // 1) не вызовется, так как состояние RadioButton устанавливается раньше, чем контрол
                //    узнаёт о наличии слушателя,
                // 2) либо из секундомеров считаются нули, так как система ещё не восстановила их состояния
                RadioGroup buttons = findViewById(R.id.discipline_selector);
                mDisciplineSelector.onCheckedChanged(buttons, buttons.getCheckedRadioButtonId());
                break;
        }
    }

    private void setDisciplineButtons() {
        RadioGroup buttons = findViewById(R.id.discipline_selector);
        buttons.setOnCheckedChangeListener(mDisciplineSelector);
        buttons.check(R.id.radio_Solo);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    private void setTouchpadListeners() {
        mTouchpad.setVisibility(View.VISIBLE);
        mTouchpad.setOnClickListener(this);
        mTouchpad.setOnLongClickListener(this);
    }

    private void removeTouchpadListeners() {
        mTouchpad.setOnClickListener(null);
        mTouchpad.setOnLongClickListener(null);
        mTouchpad.setVisibility(View.GONE);
    }

    private void TotalTimerSetup() {
        mTotalTimerView = findViewById(R.id.total_timer_view);
        mTotalTimerView.setType(ON_OFF);
    }

    private void SpecialTimerSetup() {
        mSpecialTimerView = findViewById(R.id.special_timer_view);
        mSpecialTimerView
                .setType(ON_OFF_DELAYED)
                .setStopDelayType(PrecisionChronometer.INCLUSIVE)
                .setStopDelay(mSpecialTimerStopDelay)
                .setOnChronometerHoldListener(this);

        mSpecialTimerBackgroundView = findViewById(R.id.special_timer_background);
    }

    @Override
    public void onClick(View view) {
        notifyVibrate();

        if (!mTotalTimerView.isStarted()) {
            activateSpecialTimerView();

            mTotalTimerView.start();
        } else {
            mSpecialTimerView.toggle();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (mTotalTimerView.isStarted()) {
            mSpecialTimerView.stop();
            mTotalTimerView.stop();

            activateResultsView();
            return true;
        }

        return false;
    }

    private void activateSpecialTimerView() {
        mTransitionManager.transitionTo(mSpecialTimerScene);
        mCurrentView = R.layout.special_timer_scene;

        TotalTimerSetup();

        SpecialTimerSetup();

        delayedHide(mAnimationDuration);
    }

    private void activateResultsView() {
        long totalTimeElapsed   = mTotalTimerView.getTimeElapsed();
        long specialTimeElapsed = mSpecialTimerView.getTimeElapsed();

        show();

        mSpecialTimerView.setOnChronometerHoldListener(null);

        removeTouchpadListeners();

        mTransitionManager.transitionTo(mResultsScene);
        mCurrentView = R.layout.final_results_scene;

        mTotalTimerView   = findViewById(R.id.total_timer_view);
        mSpecialTimerView = findViewById(R.id.special_timer_view);

        mTotalTimerView.setTimeElapsed(totalTimeElapsed);
        mSpecialTimerView.setTimeElapsed(specialTimeElapsed);

        setDisciplineButtons();
    }

    public void activateTotalTimerView(View view) {
        mTransitionManager.transitionTo(mTotalTimerScene);
        mCurrentView = R.layout.total_timer_scene;
        show();

        TotalTimerSetup();
        setTouchpadListeners();
    }

    private void actualizeUIVisibility() { actualizeUIVisibility(false); }
    private void actualizeUIVisibility(boolean immediately) {
        if (!mVisible) {
            hide(immediately);
        } else {
            show(immediately);
        }
    }

    @SuppressWarnings("unused")
    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() { hide(false); }
    private void hide(boolean immediately) {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);

        if(immediately)
            mHideHandler.post(mHidePart2Runnable);
        else
            mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() { show(false); }
    private void show(boolean immediately) {
        // Show the system bar
//        mTouchpad.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mTouchpad.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);

        if(immediately)
            mHideHandler.post(mShowPart2Runnable);
        else
            mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void OnHold(PrecisionChronometer chronometer) {
        TransitionDrawable transition = (TransitionDrawable) mSpecialTimerBackgroundView.getBackground();
        transition.reverseTransition(mAnimationDuration);

        notifyVibrate();
    }

    @Override
    public void OnHoldRequest(PrecisionChronometer chronometer) {
        TransitionDrawable transition = (TransitionDrawable) mSpecialTimerBackgroundView.getBackground();
        transition.startTransition(mAnimationDuration);
    }

    @Override
    public void OnHoldCancel(PrecisionChronometer chronometer) {
        TransitionDrawable transition = (TransitionDrawable) mSpecialTimerBackgroundView.getBackground();
        transition.reverseTransition(mAnimationDuration);

        notifyVibrate();
    }

    @SuppressWarnings("deprecation")
    private void notifyVibrate() {
        if(mNotifyVibrate)
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(100);
    }

    public void ShowSettings(MenuItem item) {
        startActivity(new Intent(this, StopwatchPreferences.class));
    }
}
