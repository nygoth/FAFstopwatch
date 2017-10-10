package ru.stage_sword.fafstopwatch;

import android.annotation.SuppressLint;
import android.support.v7.app.ActionBar;
import android.content.Context;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.transition.Scene;
import android.support.transition.TransitionInflater;
import android.support.transition.TransitionManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import static ru.stage_sword.fafstopwatch.PrecisionChronometer.DELAYED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.ON_HOLD;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.ON_OFF;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.ON_OFF_DELAYED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.PAUSED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.STARTED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.STOPPED;
import static ru.stage_sword.fafstopwatch.PrecisionChronometer.UNKNOWN_STATE;

public class StopwatchActivity extends AppCompatActivity
        implements  View.OnClickListener,
                    View.OnLongClickListener,
                    PrecisionChronometer.OnChronometerHoldListener {
    private static final String TAG = "FAF";

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 200;
    private static final String UI_VISIBLE_STATE           = "uiVisible";
    private static final String CURRENT_VIEW               = "currentView";
    private static final String TOTAL_TIMER_BASE           = "totalTimeBase";
    private static final String SPECIAL_TIMER_BASE         = "specialTimeBase";
    private static final String TOTAL_TIMER_VALUE          = "totalTimeElapsed";
    private static final String SPECIAL_TIMER_VALUE        = "specialTimeElapsed";
    private static final String TOTAL_TIMER_STATE          = "totalTimerState";
    private static final String SPECIAL_TIMER_STATE        = "specialTimerState";
    private static final String SPECIAL_TIMER_TIME_TO_HOLD = "specialTimerTimeToHold";

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
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
//            if (AUTO_HIDE) {
//                delayedHide(AUTO_HIDE_DELAY_MILLIS);
//            }
            return false;
        }
    };
    private PrecisionChronometer mSpecialTimerView;
    private final Handler mHideHandler = new Handler();
    private PrecisionChronometer mTotalTimerView;
    private ViewGroup mSceneRoot;
    private Scene mTotalTimerScene;
    private Scene mSpecialTimerScene;
    private Scene mResultsScene;
    private View mTouchpad;
    private TransitionManager mTransitionManager;
    private int mAnimationDuration;
    private View mSpecialTimerBackgroundView;
    private static int currentView = R.layout.total_timer_scene;
    private int mSpecialTimerStopDelay;
    private boolean mNotifyVibrate;

    //    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//    }
//
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(CURRENT_VIEW, currentView);

        if(currentView != R.layout.total_timer_scene) {
            mNotifyVibrate = false;

            outState.putBoolean(UI_VISIBLE_STATE, mVisible);

//            outState.putLong(TOTAL_TIMER_BASE,          mTotalTimerView.getBase());
//            outState.putLong(SPECIAL_TIMER_BASE,        mSpecialTimerView.getBase());
//            outState.putLong(TOTAL_TIMER_VALUE,         mTotalTimerView.getTimeElapsed());
//            outState.putLong(SPECIAL_TIMER_VALUE,       mSpecialTimerView.getTimeElapsed());
//            outState.putInt (TOTAL_TIMER_STATE,         mTotalTimerView.getState());
            outState.putInt (SPECIAL_TIMER_STATE,       mSpecialTimerView.getState());
//            outState.putInt (SPECIAL_TIMER_TIME_TO_HOLD,mSpecialTimerView.getTimeToHold(true));
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_stopwatch);

        long totalTimerBase = 0;
        long specialTimerBase = 0;
        long totalTimerElapsed = 0;
        long specialTimerElapsed = 0;
//        int totalTimerState = UNKNOWN_STATE;
        int specialTimerState = UNKNOWN_STATE;

        mNotifyVibrate = true;
        mVisible = true;
        if (savedInstanceState != null) {
            mVisible = savedInstanceState.getBoolean(UI_VISIBLE_STATE, true);

            currentView         = savedInstanceState.getInt (CURRENT_VIEW, R.layout.total_timer_scene);
//            totalTimerBase      = savedInstanceState.getLong(TOTAL_TIMER_BASE, 0);
//            specialTimerBase    = savedInstanceState.getLong(SPECIAL_TIMER_BASE, 0);
//            totalTimerElapsed   = savedInstanceState.getLong(TOTAL_TIMER_VALUE, 0);
//            specialTimerElapsed = savedInstanceState.getLong(SPECIAL_TIMER_VALUE, 0);
//            totalTimerState     = savedInstanceState.getInt (TOTAL_TIMER_STATE, UNKNOWN_STATE);
            specialTimerState   = savedInstanceState.getInt (SPECIAL_TIMER_STATE, UNKNOWN_STATE);
//            timeToHold          = savedInstanceState.getInt (SPECIAL_TIMER_TIME_TO_HOLD, 0);
        }

//        mControlsView = findViewById(R.id.fullscreen_content_controls);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        mSceneRoot = findViewById(R.id.timer_scene_root);

        mTotalTimerScene   = Scene.getSceneForLayout(mSceneRoot, R.layout.total_timer_scene,   this);
        mSpecialTimerScene = Scene.getSceneForLayout(mSceneRoot, R.layout.special_timer_scene, this);
        mResultsScene      = Scene.getSceneForLayout(mSceneRoot, R.layout.final_results_scene, this);

        LayoutInflater.from(this).inflate(currentView,mSceneRoot);

        TransitionInflater inflater = TransitionInflater.from(this);
        mTransitionManager = inflater.inflateTransitionManager(R.transition.chronometer_transitions, mSceneRoot);

        mAnimationDuration = getResources().getInteger(R.integer.timer_animation_duration);
        mSpecialTimerStopDelay = getResources().getInteger(R.integer.special_timer_default_stop_delay);

        mTouchpad = findViewById(R.id.touchpad);
        setTouchpadListeners();

        TotalTimerSetup();
//        applyTimerState(mTotalTimerView,   totalTimerState,   timeToHold);
//        if (totalTimerBase != 0)
//            mTotalTimerView.setBase(totalTimerBase);

        switch (currentView) {
            case R.layout.special_timer_scene:
                SpecialTimerSetup();
                if (specialTimerState == DELAYED) { // Изменим цвет фона для соответствующего состояния
                    TransitionDrawable trans = (TransitionDrawable) mSpecialTimerView.getBackground();
                    trans.resetTransition();
                    trans.startTransition(0);
                }

//                if (specialTimerBase != 0 && specialTimerElapsed != 0)
//                    mSpecialTimerView.setBase(specialTimerBase);

                break;
            case R.layout.final_results_scene:
                mSpecialTimerView = findViewById(R.id.special_timer_view);
//                if (specialTimerBase != 0)
//                    mSpecialTimerView.setTimeElapsed(specialTimerElapsed);
//
//                if (totalTimerBase != 0)
//                    mTotalTimerView.setTimeElapsed(totalTimerElapsed);
                break;
        }

        actualize();
    }

//    private void applyTimerState(PrecisionChronometer timer, int state) {
//        switch (state) {
//            case STOPPED:
//                timer.stop();
//                break;
//            case STARTED:
//                timer.start();
//                break;
//            case PAUSED:
//            case ON_HOLD:
//                timer.toggle(state == STARTED);
//                break;
//            case DELAYED:
//                // Здесь мы запускаем хронометр и сразу же переключаем его в режим паузы
//                // Но выставляем не задержку по умолчанию, а свою, требуемую.
//                // FIXME Здесь будет глючить таймер EXCLUSIVE. Он запоминает HoldTime, а потом мы меняем базу
//                // FIXME В результате, всё будет некорректно. Для INCLUSIVE таймера пофиг, он всё равно
//                // FIXME перезапоминает холд тайм по стопу, и что, что где-то на холд реквест интервале
//                // FIXME поменяли базу, ему пофиг
//                timer.toggle(true);
////                timer.toggle(timeToHold);
//                break;
//        }
//    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
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
        mTotalTimerView
                .setType(ON_OFF)
                .resetBase();
    }

    private void SpecialTimerSetup() {
        mSpecialTimerView = findViewById(R.id.special_timer_view);
        mSpecialTimerView
                .setType(ON_OFF_DELAYED)
                .setStopDelayType(PrecisionChronometer.INCLUSIVE)
                .setStopDelay(mSpecialTimerStopDelay)
                .setOnChronometerHoldListener(this)
                .resetBase();

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
        currentView = R.layout.special_timer_scene;

        TotalTimerSetup();

        SpecialTimerSetup();

        delayedHide(mAnimationDuration);
    }

    private void activateResultsView() {
        show();
        long totalTimeElapsed   = mTotalTimerView.getTimeElapsed();
        long specialTimeElapsed = mSpecialTimerView.getTimeElapsed();

        mSpecialTimerView.setOnChronometerHoldListener(null);

        removeTouchpadListeners();

        mTransitionManager.transitionTo(mResultsScene);
        currentView = R.layout.final_results_scene;

        mTotalTimerView   = findViewById(R.id.total_timer_view);
        mSpecialTimerView = findViewById(R.id.special_timer_view);

        mTotalTimerView.setTimeElapsed(totalTimeElapsed);
        mSpecialTimerView.setTimeElapsed(specialTimeElapsed);
    }

    public void activateTotalTimerView(View view) {
        mTransitionManager.transitionTo(mTotalTimerScene);
        currentView = R.layout.total_timer_scene;
        show();

        TotalTimerSetup();
        setTouchpadListeners();
    }

    private void actualize() { acrualize(false); }
    private void acrualize(boolean immediately) {
        if (!mVisible) {
            hide(immediately);
        } else {
            show(immediately);
        }
    }
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

    private void notifyVibrate() {
        if(mNotifyVibrate)
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(100);
    }

    public void ShowSettings(MenuItem item) {
    }
}
