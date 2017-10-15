package ru.stage_sword.fafstopwatch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import java.lang.ref.WeakReference;


/**
 * Created by nygoth on 22.09.2017.
 * Precision chronometer that shows milliseconds
 *
 * Code based on revised Android Chronometer by antoniom
 *
 * Логика работы хронометров следующая.
 * Есть интерфейс, состоящий из функций start(), stop(), toggle(), hold()
 *
 * Первые две функции ВСЕГДА включают и выключают хронометр полностью.
 * Т.е. старт происходит с нуля, стоп окончательный.
 *
 * Вторые две функции по сути синонимы. Только toggle() переключает состояние
 * секундомера, а setStarted() устанавливает требуемое.
 * И на их работу влияет тип секундомера.
 * CHRONOMETER_TYPE_ON_OFF:
 *      *  при остановке секундомера отсчёт останавливается на том месте
 *         на котором произошла остановка.
 *      *  при запуске секундомера осчёт продолжается с того места,
 *         на котором он был остановлен.
 *
 * CHRONOMETER_TYPE_CONTINUOUS:
 *      *  при остановке секундомера происходит только останов отсчёта времени
 *         на экране. Само время продолжает отсчитываться.
 *      *  старт лишь запускает отображение отсчёта на экране. Для сброса и
 *         полного останова секундомера нужен вызов stop().
 *
 * CHRONOMETER_TYPE_ON_OFF_DELAYED:
 *      *  при останове секундомера отсчёт не останавливается ещё некоторое
 *         количество времени, установленное вызовом setStopDelay().
 *         Если отсчёт запущен заново до истечения этого времени, то секундомер
 *         будет работать как CHRONOMETER_TYPE_CONTINUOUS.
 *         Если указанное количество времени истечёт, а секундомер не будет запущен,
 *         то отсчёт остановится либо на отметке, когда была выполнена команда
 *         останова (EXCLUSIVE), либо на отметке, когда закончился интервал (INCLUSIVE).
 *         Это устанавливается функцией setStopDelayType()
 *
 *         Функции setStopDelay() и setStopDelayType() не имеют смысла для других типов
 *         секундомера
 *
 * Функция hold() приостанавливает отсчёт любого типа хронометра немедленно, но не сбрасывает
 * его. По сути, она позволяет превратить хронометры CHRONOMETER_TYPE_CONTINUOUS и CHRONOMETER_TYPE_ON_OFF_DELAYED
 * в хронометр CHRONOMETER_TYPE_ON_OFF. Запуск хронометра после этого вызова производится либо
 * вызовом start(), либо toggle(). Оба варианта равнозначны и взаимозаменяемы
 *
 * Данные функции управляют рабочим циклом хронометра. Кроме них есть ещё сервисные функции, позволяющие
 * сохранить и восстановить состояние секундомера на текущий момент времени.
 * Суть данной процедуры следующая.
 *
 * После своего создания секундомер находится в состоянии freeze(). Это подразумевает, что все его параметры
 * заморожены
 * Команда start() размораживает состояние секундомера и инициализирует его начальными значениями.
 * Т.е. стартовое время 0:00.00, никаких ожидающих запросов на останов и прочее. Ну и запускает отсчёт.
 *
 * Но разморозить секундомер можно и иначе. Смысл в том, что в замороженном состоянии можно настроить
 * параметры секундомера так, как необходимо. Начальное время, наличие запроса на останов, или вообще
 * остановленное состояние (собственно, рабочий статус), время до останова и т.п.
 * И после этого просто разморозить секундомер. И он продолжит свою работу с точки заморозки.
 * Делается это командой unfreeze().
 *
 * Команда stop() не помещает секундомер в замороженное состояние.
 *
 * В размороженном состоянии функции установки рабочих параметров секундомера не работают.
 * Думаю, они должны выбрасывать исключение.
 *
 * Какие параметры относятся к рабочему состоянию секундомера:
 *      Отсчитанное время            -- elapsedTime
 *      База отсчёта                 -- base
 *      Время останова               -- holdTime, имеет смысл только если статус секундомера ON_HOLD
 *      Время запроса на останов     -- holdRequestTime, имеет смысл только если статус DELAYED
 *      Оставшееся время до останова -- timeToHold, имеет смысл только если статус DELAYED
 *      Статус секундомера           -- state, текущий рабочий режим. STOPPED, STARTED, ON_HOLD, PAUSED, DELAYED
 *                                      первые три состояния могут принимать любые типы секундомера,
 *                                      PAUSED -- только CHRONOMETER_TYPE_CONTINUOUS, а
 *                                      DELAYED -- только CHRONOMETER_TYPE_ON_OFF_DELAYED
 *
 * Получить все эти параметры можно и в размороженном состоянии. Заморозка гарантирует, что они не изменятся в
 * процессе считывания и будут актуальны до тех пор, пока секундомер не разморозят.
 */

@SuppressWarnings("unused")
public class PrecisionChronometer extends AppCompatTextView {
    @SuppressWarnings("unused")
    private static final String TAG = "PrecisionChronometer";

   /*
    * Chronometer types
    */
    public static final int UNKNOWN        = 0; // Strange Stopwatch
    public static final int CONTINUOUS     = 1; // Stopwatch, that continue counting after start until it is explicitly reset
    public static final int ON_OFF         = 2; // Just start and stop by command
    public static final int ON_OFF_DELAYED = 3; // Stop after given delay from stop command

    /*
     * Delay types
     */
    public static final int EXCLUSIVE = 1; // Delayed chronometer stop counting at the stop point after given delay
    public static final int INCLUSIVE = 2; // Delayed chronometer stop counting after given delay

    /*
     * Private values
     * Message ids for handler
     */
    private static final int TICK_MESSAGE = 2;
    private static final int DELAYED_HOLD_MESSAGE = 3;

    /*
     * События для распределения
     */
    private enum Event {
        START,
        STOP,
        RESTART,
        HOLD,
        HOLD_REQUEST,
        HOLD_CANCEL,
        PAUSE,
        UNPAUSE
    }

    public static final int UNKNOWN_STATE = 0;
    public static final int STARTED = 1;
    public static final int DELAYED = 2;
    public static final int ON_HOLD = 3;
    public static final int PAUSED  = 4;
    public static final int STOPPED = 5;

    interface OnChronometerTickListener {

        void onTick(PrecisionChronometer chronometer);
    }

    interface OnChronometerStartStopListener {
        /*
         * Вызывается по старту хронометра с нуля. При перезапусках по toggle() не вызывается
         * Вызывается до отрисовки старта и вызова первого тика хронометра
         */
        void OnStart(PrecisionChronometer chronometer);

        /*
         * Вызывается по полному останову хронометра. При задержках по hold() или toggle() -- не вызывается
         * Вызывается после отрисовки останова и последнего тика хронометра
         */
        void OnStop(PrecisionChronometer chronometer);

        /*
         * Вызывается по перезапуску хронометра после hold()
         * Вызывается до отрисовки
         */
        void OnRestart(PrecisionChronometer chronometer);
    }

    interface OnChronometerHoldListener {

        /*
         * Вызывается при задержке по hold(). По реальному стопу
         * Вызывается после отрисовки
         */
        void OnHold(PrecisionChronometer chronometer);

        /*
         * Вызывается при запросе на останов у CHRONOMETER_TYPE_ON_OFF_DELAYED
         * Вызывается до посылки сообщения об останове
         */
        void OnHoldRequest(PrecisionChronometer chronometer);

        /*
         * Вызывается при отмене запроса на останов, когда он ещё не успел сработать
         * CHRONOMETER_TYPE_ON_OFF_DELAYED
         * Вызывается после удаления сообщения из очереди
         */
        void OnHoldCancel(PrecisionChronometer chronometer);
    }

    /*
     * Данный интерфейс актуалены только для CHRONOMETER_TYPE_CONTINUOUS
     */
    interface OnChronometerPauseListener {
        /*
         * Вызывается при постановке на паузу CHRONOMETER_TYPE_CONTINUOUS
         * Вызывается после отрисовки
         */
        void OnPause(PrecisionChronometer chronometer);

        /*
         * Вызывается при снятии с паузы CHRONOMETER_TYPE_CONTINUOUS
         */
        void OnUnpause(PrecisionChronometer chronometer);
    }

    private static class innerHandler extends Handler {
        private final WeakReference<PrecisionChronometer> m_pHandlerHolder;

        innerHandler(PrecisionChronometer holder) {
            m_pHandlerHolder = new WeakReference<>(holder);
        }

        @Override
        public void handleMessage(Message msg) {
            PrecisionChronometer holder = m_pHandlerHolder.get();
            if (holder != null) {
                if (!holder.isRunning()) return;

                switch (msg.what) {
                    case TICK_MESSAGE:
                        holder.updateText(SystemClock.elapsedRealtime());
                        holder.dispatchTick();
                        sendMessageDelayed(Message.obtain(this, TICK_MESSAGE), 97);
                        break;
                    case DELAYED_HOLD_MESSAGE:
                        holder.hold();
                        break;
                }
            }
        }
    }

    /*
     * Ключи для сохранения и восстановления состояния
     */
    private static final String STATE_VALUE_INITIALISED = "initialised";
    private static final String STATE_VALUE_STARTED     = "started";
    private static final String STATE_VALUE_ON_HOLD     = "on_hold";
    private static final String STATE_VALUE_DELAYED     = "delayed";
    private static final String STATE_VALUE_BASE        = "base";
    private static final String STATE_VALUE_HOLD_TIME   = "hold_time";
    private static final String STATE_VALUE_FREEZE_TIME = "freeze_time";
    private static final String STATE_VALUE_TYPE        = "type";
    private static final String STATE_VALUE_STOP_DELAY  = "stop_delay";
    private static final String STATE_VALUE_DELAY_TYPE  = "delay_type";
    private static final String STATE_VALUE_SUPERSTATE  = "super_state";

    private final Handler mHandler = new innerHandler(this);

    /*
     * Переменные, определяющие тип и состояние секундомера
     */
    private int mChronometerType;

    // Рабочее состояние секундомера
    private boolean mInitialised;   // Инициализирован, т.е. из нулевого состояния была запущена функция старта
    private boolean mVisible;       // Виджет видим
    private boolean mStarted;       // Запущен, т.е. идёт актуальный отсчёт времени
    private boolean mRunning;       // Отображается отсчёт времени в виджете
    private boolean mOnHold;        // Секундомер приостановлен
    private boolean mDelayed;       // Секундомер в ожидании остановки (т.е. команда на останов получена, но отсчёт ещё идёт)
    private boolean mFrozen;        // Заморожен. Ничего не происходит, но состояние секундомера такое, какое было при работе

    private long m_aBase;           // База отсчёта времени, абсолютное значение
    private long m_aHoldTime;       // Момент приостановки секундомера, абсолютное значение
    private long m_aFreezeTime;     // Момент заморозки секундомера, абсолютное значение

    private int mStopDelay;
    private int mDelayType;

    private OnChronometerTickListener      mOnChronometerTickListener;
    private OnChronometerStartStopListener mOnChronometerStartStopListener;
    private OnChronometerHoldListener      mOnChronometerHoldListener;
    private OnChronometerPauseListener     mOnChronometerPauseListener;

    public PrecisionChronometer(Context context) {
        this (context, null, 0);
    }

    public PrecisionChronometer(Context context, AttributeSet attrs) {
        this (context, attrs, 0);
    }

    public PrecisionChronometer(Context context, AttributeSet attrs, int defStyle) {
        super (context, attrs, defStyle);

        mChronometerType = ON_OFF;
        mStopDelay = 10000; // Ten seconds by default
        mDelayType = INCLUSIVE;
        init();

        mOnChronometerTickListener      = null;
        mOnChronometerHoldListener      = null;
        mOnChronometerPauseListener     = null;
        mOnChronometerStartStopListener = null;
    }

    private void init() {
        m_aBase = SystemClock.elapsedRealtime();
        if(!mFrozen) {
            mDelayed = mRunning = mOnHold = mInitialised = false;
            updateText(m_aBase);
        }
    }

    public PrecisionChronometer setOnChronometerTickListener  (OnChronometerTickListener listener)
            { mOnChronometerTickListener = listener; return this; }
    public PrecisionChronometer setOnChronometerStartStopListener(OnChronometerStartStopListener listener)
            { mOnChronometerStartStopListener = listener; return this; }
    public PrecisionChronometer setOnChronometerHoldListener (OnChronometerHoldListener listener)
            { mOnChronometerHoldListener = listener; return this; }
    public PrecisionChronometer setOnChronometerPauseListener (OnChronometerPauseListener listener)
            { mOnChronometerPauseListener = listener; return this; }

    public OnChronometerTickListener      getOnChronometerTickListener()      { return mOnChronometerTickListener; }
    public OnChronometerStartStopListener getOnChronometerStartStopListener() { return mOnChronometerStartStopListener; }
    public OnChronometerHoldListener      getOnChronometerHoldListener()      { return mOnChronometerHoldListener; }
    public OnChronometerPauseListener     getOnChronometerPauseListener()     { return mOnChronometerPauseListener; }

    public void start() {
        if (mFrozen)
            unfreeze();

        if(mStarted) return;

        mStarted = true;
        _toggle();
    }

    public void stop() {
        if(!mInitialised || mFrozen) return;

        if(!mOnHold)
            hold();

        mOnHold = mInitialised = false;
        dispatchEvent(Event.STOP);
    }

    public void toggle() {
        if (mFrozen) return;

        mStarted = !mStarted;

        _toggle();
    }

    public void toggle(boolean started) {
        if(mOnHold && !started || mStarted == started || mFrozen) return;

        mStarted = started;

        _toggle();
    }

    /*
     * Функция, по сути, занимается актуализацией состояния секундомера, заданного переменной mStarted
     */
    private void _toggle() {
        mOnHold = false;

        if(!mInitialised && mStarted) {
            resetBase();
            mInitialised = true;
            dispatchEvent(Event.START);
        } else
            changeChronometerState();

        updateRunning();
    }

    public void hold() {
        _hold();
        updateRunning();
    }

    private void _hold() {
        /*
         * Для хронометра типа CHRONOMETER_TYPE_ON_OFF_DELAYED с фиксацией по точке останова (EXCLUSIVE)
         * m_aHoldTime уже установлена на момент нажатия стопа. Во всех других случаях устанавливаем
         * эту переменную на момент выполнения останова хронометра.
         */
        mHandler.removeMessages(TICK_MESSAGE);
        mHandler.removeMessages(DELAYED_HOLD_MESSAGE);
        if(mDelayType != EXCLUSIVE || mChronometerType != ON_OFF_DELAYED)
            m_aHoldTime = SystemClock.elapsedRealtime();

        mDelayed = mStarted = false;
        mOnHold = true;

        dispatchEvent(Event.HOLD);

    }

    private void _unhold() {
        m_aBase += m_aHoldTime != 0 ? (SystemClock.elapsedRealtime() - m_aHoldTime) : 0;
        m_aHoldTime = 0;
        dispatchEvent(Event.RESTART);
    }

    /*
     * При вызове этой функции состояние хронометра не изменено.
     * Изменён только один флаг -- mStarted -- на противоположный.
     * Вот актуализацией этих изменений функция и занимается
     */
    private void changeChronometerState() {
        if(mStarted) { // Если секундомер запускается, то для разных типов секундомера выполняем разные действия
            switch (mChronometerType) {
                case ON_OFF:
                    _unhold();
                    break;

                /*
                 * Итак, что мы делаем, если секундомер CHRONOMETER_TYPE_ON_OFF_DELAYED запускается.
                 * Если старт происходит до срабатывания события на запрос останова, то оно удаляется из очереди,
                 * и всё идёт так, как будто ничего не было.
                 * Иначе вызывается _unhold()
                 */
                case ON_OFF_DELAYED:
                    if (mDelayed) {
                        mHandler.removeMessages(DELAYED_HOLD_MESSAGE);
                        dispatchEvent(Event.HOLD_CANCEL);
                        mDelayed = false;
                    } else
                        _unhold();
                    break;

                /*
                 * Старт и стоп секундомера CHRONOMETER_TYPE_CONTINUOUS похож на таковой у CHRONOMETER_TYPE_ON_OFF
                 * Но при старте мы не корректируем m_aBase, и всё.
                 */
                case CONTINUOUS:
                    mStarted = true;
                    m_aHoldTime = 0;
                    dispatchEvent(Event.UNPAUSE);
                    break;
            }

        } else { // Если секундомер останавливается, то выполняем иные действия
            switch (mChronometerType) {
                case ON_OFF:
                    _hold();
                    break;

                /*
                 * Итак, что мы делаем, если секундомер CHRONOMETER_TYPE_ON_OFF_DELAYED останавливается.
                 * Мы запоминаем время останова и запускаем в будущее на DelayTime вперёд событие останова.
                 * Когда это событие случается, оно останавливает секундомер, вызывая _hold()
                 */
                case ON_OFF_DELAYED:
                    mDelayed = true;
                    m_aHoldTime = SystemClock.elapsedRealtime();
                    dispatchEvent(Event.HOLD_REQUEST);
                    mHandler.sendMessageDelayed(Message.obtain(mHandler, DELAYED_HOLD_MESSAGE), mStopDelay);
                    break;

                case CONTINUOUS:
                    mStarted = false;
                    m_aHoldTime = SystemClock.elapsedRealtime();
                    dispatchEvent(Event.PAUSE);
                    break;
            }
        }
    }

    private synchronized void updateRunning() {
        if(mFrozen) return;

        boolean running = mVisible && (mStarted || mDelayed);

        if (running != mRunning) {
            updateText(getCurrentChronometerAbsoluteTime());

            if (running) {
                dispatchTick();
                mHandler.sendMessageDelayed(Message.obtain(mHandler, TICK_MESSAGE), 10);
            } else {
                mHandler.removeMessages(TICK_MESSAGE);
            }
            mRunning = running;
        }
    }

    private synchronized void updateText(long now) { setText(getHumanReadableTime(now - m_aBase)); }

    @SuppressLint("DefaultLocale")
    public static String getHumanReadableTime(long time) {
        int hours = (int)(time / (3600 * 1000));
        int remaining = (int)(time % (3600 * 1000));

        int minutes = remaining / (60 * 1000);
        remaining = remaining % (60 * 1000);

        int seconds = remaining / 1000;

        int milliseconds = ((int)time % 1000) / 10;

        String text = (hours > 0) ? String.format("%02d:", hours): "";

        text += String.format("%02d:%02d.%02d", minutes, seconds, milliseconds);

        return text;
    }

    public PrecisionChronometer freeze() {
        if(!mFrozen) {
            mFrozen = true;
            m_aFreezeTime = SystemClock.elapsedRealtime();

            mHandler.removeMessages(TICK_MESSAGE);
            mHandler.removeMessages(DELAYED_HOLD_MESSAGE);
        }

        return this;
    }

    /*
     * Что нужно делать при разморозке из разных состояний
     * STARTED -- секундомер просто шёл. Поэтому нужно заново запустить сообщение
     *            TICK_MESSAGE и обновить виджет
     *
     * STOPPED -- секундомер остановлен. Вообще ничего не нужно делать, кроме обновления
     *            экрана
     *
     * PAUSED  -- обновить экран на начальное значение, т.е. то, при котором секундомер
     *           встал на паузу. Запустить отсчёт с учётом того интервала, что отсчитал
     *           секундомер, будучи на паузе, но до заморозки.
     *
     * ON_HOLD -- отобразить на экране время постановки на холд. То же, по сути, что и
     *            STOPPED. Разница в значениях флагов, а они уже выставлены как надо.
     *
     * DELAYED -- запустить секундомер с момента, на котором он был заморожен, обновить экран
     *            и запустить сообщение об останове с задержкой, на которую не дождалось
     *            данное сообщение при заморозке.
     *
     * При разморозке никаких событий не посылается вообще. Только тики в своё время.
     *
     * При вызове этой функции предполагается, что все флаги и переменные установлены в
     * актуальное состояние.
     */
    public void unfreeze() {
        if(!mFrozen)
            return;

        mFrozen = false;
        long now = SystemClock.elapsedRealtime();
        long frozenInterval = now - m_aFreezeTime;    // Это тот интервал, на который должны быть скорректированы
                                                      // глобальные значения времени -- база, время останова
        long delayTimePassed = m_aFreezeTime - m_aHoldTime;

        // Если база больше, чем время заморозки, то, вероятно, её установили в тот момент, когда секундомер был заморожен
        // В этом случае устанавливаем её на момент разморозки. Иначе корректируем на время заморозки.
        m_aBase = m_aBase >= m_aFreezeTime ? now : m_aBase + frozenInterval;

        // Так же поступаем и со временем останова, правда я слабо себе представляю
        // ситуацию, когда оно может оказаться больше времени заморозки.
        // Все функции, управляющие состоянием секундомера, не срабатывают во время заморозки, а иначе
        // установить это время сложно
        if(m_aHoldTime != 0)
            m_aHoldTime = m_aHoldTime > m_aFreezeTime ? now: m_aHoldTime + frozenInterval;

        m_aFreezeTime = 0;
        if(mDelayed)
            mHandler.sendMessageDelayed(Message.obtain(mHandler, DELAYED_HOLD_MESSAGE), mStopDelay - delayTimePassed);

        // Только в этих двух случаях секундомер отображается отсчитывающим время
        if(mStarted || mDelayed)
            updateRunning();
        else
            updateText(m_aHoldTime != 0 ? m_aHoldTime : m_aBase);
    }

    public PrecisionChronometer setBase(long base) {
        m_aBase = base;
        if(!mFrozen) {
            updateText(getCurrentChronometerAbsoluteTime());
            dispatchTick();
        } else {
            // Чтобы хронометр не ушёл в минус при смене базы в замороженном состоянии
            if (m_aFreezeTime < m_aBase)
                m_aFreezeTime = m_aBase;
        }

        // Фишка в том, что вызов этой функции на неинициализированном секундомере
        // приведёт к тому, что он окажется в состоянии PAUSED. Т.е., по факту,
        // идущим, но не отображающим ход времени. В результате getTimeElapsed()
        // будет возвращать всегда новое значение. Это неприемлимо, поэтому, если
        // при установке базы у нас секундомер не запущен, то ставим его на холд.
        mInitialised = true;
        if(!mStarted && !mOnHold && !mDelayed)
            mOnHold = true;

        return this;
    }

    /*
     * resetBase не устанавливает mInitialised в true
     * Зато это делает setBase
     *
     * Смысл в том, что сбрасывая базу, мы устанавливаем в виджете 0
     * и обнуляем флаги, так что при старте система вновь установит
     * базу на момент старта
     *
     * А если мы базу явно устанавливаем, то старт не должен её менять
     * Иначе какой смысл её устанавливать.
     */
    public PrecisionChronometer resetBase() {
        init();
        if(!mFrozen)
            dispatchTick();
        return this;
    }

    /*
     * Что мы должны вернуть, как прошедшее время, для разных состояний хронометра:
     * STARTED -- интервал от текущего времени до базы            (mStarted == true)
     * DELAYED -- интервал от текущего времени до базы            (mDelayed == true)
     * PAUSED  -- интервал от текущего времени до базы            (mStarted == false && mInitialised == true)
     * ON_HOLD -- интервал от времени постановки на холд до базы  (mOnHold  == true)
     * STOPPED -- интервал от времени останова до базы            (mStarted == false && mInitialised = false)
     * FROZEN  -- время от момента заморозки до базы              (mFrozen  == true), если STARTED, DELAYED или PAUSED
     */
    private long getCurrentChronometerAbsoluteTime() {
        long value = m_aHoldTime == 0 ? m_aBase : m_aHoldTime;
        boolean paused = !mStarted && mInitialised && !mOnHold;

        value =  mStarted || mDelayed || paused ?
                (mFrozen ? m_aFreezeTime : SystemClock.elapsedRealtime()) :
                value;

        return value;
    }

    /*
     * setBase тоже вызывает getCurrentChronometerAbsoluteTime(). Чтобы результаты были корректными,
     * необходимо изменять данные после первого вызова, но до установки новой базы
     */
    public PrecisionChronometer setTimeElapsed  (long elapsed) {
        long base = getCurrentChronometerAbsoluteTime();
        if(m_aHoldTime == 0)
            m_aHoldTime = base;

        setBase(base - elapsed);

        return this;
    }

    public PrecisionChronometer setType         (int type)      { mChronometerType = type; return this;}
    public PrecisionChronometer setStopDelay    (int stopDelay) { mStopDelay = stopDelay;  return this; }
    public PrecisionChronometer setStopDelayType(int type)      { mDelayType = type;       return this; }

    public boolean isStarted()            { return mStarted; }
    public boolean isFrozen()             { return mFrozen; }
    public int     getType()              { return mChronometerType; }
    public int     getStopDelay()         { return mStopDelay; }
    public int     getStopDelayType()     { return mDelayType; }
    public long    getTimeElapsed()       { return getCurrentChronometerAbsoluteTime() - m_aBase; }
    public String  getTimeElapsedString() { return getHumanReadableTime(getTimeElapsed()); }
    public long    getBase()              { return m_aBase; }

    protected boolean isRunning() { return mRunning; }

    /*
     * Переменные состояния, актуальные для секундомера
     * mStarted, mOnHold, mDelayed
     *
     * Состояние секундомера определяется следующей таблицей:
     * ---------+---------+----------+------------
     * mStarted | mOnHold | mDelayed | Состояние
     * ---------+---------+----------+------------
     *  false   |  false  |  false   |  STOPPED если mInitialised = false, иначе PAUSED
     *  false   |  false  |  true    |  DELAYED
     *  false   |  true   |  false   |  ON_HOLD
     *  false   |  true   |  true    |  ON_HOLD
     *  true    |  false  |  false   |  STARTED
     *  true    |  false  |  true    |  STARTED
     *  true    |  true   |  false   |  STARTED
     *  true    |  true   |  true    |  STARTED
     */
    public int getState() {
        return mStarted ? STARTED :
                !mInitialised ? STOPPED :
                        mOnHold  ? ON_HOLD :
                                mDelayed ? DELAYED : PAUSED;
    }

    public int getTimeToHold() {
        int timeToHold = 0;

        if(mDelayed) {
            timeToHold = (int)(m_aHoldTime + mStopDelay - SystemClock.elapsedRealtime());

        }
        return timeToHold;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning();
    }

    @Override
    public Parcelable onSaveInstanceState()
    {
        freeze();

        Bundle bundle = new Bundle();
        bundle.putParcelable(STATE_VALUE_SUPERSTATE, super.onSaveInstanceState());

        bundle.putInt(STATE_VALUE_TYPE, mChronometerType);

        bundle.putBoolean(STATE_VALUE_INITIALISED, mInitialised);
        bundle.putBoolean(STATE_VALUE_STARTED,     mStarted);
        bundle.putBoolean(STATE_VALUE_ON_HOLD,     mOnHold);
        bundle.putBoolean(STATE_VALUE_DELAYED,     mDelayed);

        bundle.putLong(STATE_VALUE_BASE,        m_aBase);
        bundle.putLong(STATE_VALUE_HOLD_TIME,   m_aHoldTime);
        bundle.putLong(STATE_VALUE_FREEZE_TIME, m_aFreezeTime);

        bundle.putInt(STATE_VALUE_STOP_DELAY, mStopDelay);
        bundle.putInt(STATE_VALUE_DELAY_TYPE, mDelayType);

        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state)
    {
        freeze();
        if (state instanceof Bundle) // implicit null check
        {
            Bundle bundle = (Bundle) state;

            mChronometerType = bundle.getInt(STATE_VALUE_TYPE);
            mStopDelay       = bundle.getInt(STATE_VALUE_STOP_DELAY);
            mDelayType       = bundle.getInt(STATE_VALUE_DELAY_TYPE);

            mInitialised = bundle.getBoolean(STATE_VALUE_INITIALISED);
            mStarted     = bundle.getBoolean(STATE_VALUE_STARTED);
            mOnHold      = bundle.getBoolean(STATE_VALUE_ON_HOLD);
            mDelayed     = bundle.getBoolean(STATE_VALUE_DELAYED);

            m_aBase       = bundle.getLong(STATE_VALUE_BASE);
            m_aHoldTime   = bundle.getLong(STATE_VALUE_HOLD_TIME);
            m_aFreezeTime = bundle.getLong(STATE_VALUE_FREEZE_TIME);

            state = bundle.getParcelable(STATE_VALUE_SUPERSTATE);
        }
        super.onRestoreInstanceState(state);
        unfreeze();
    }

    void dispatchEvent(Event type) {
        if (mOnChronometerStartStopListener != null) {
            switch (type) {
                case START:
                    mOnChronometerStartStopListener.OnStart(this);
                    break;
                case RESTART:
                    mOnChronometerStartStopListener.OnRestart(this);
                    break;
                case STOP:
                    mOnChronometerStartStopListener.OnStop(this);
                    break;
            }
        }

        if (mOnChronometerHoldListener != null) {
            switch (type) {
                case HOLD:
                    mOnChronometerHoldListener.OnHold(this);
                    break;
                case HOLD_REQUEST:
                    mOnChronometerHoldListener.OnHoldRequest(this);
                    break;
                case HOLD_CANCEL:
                    mOnChronometerHoldListener.OnHoldCancel(this);
                    break;
            }
        }

        if(mOnChronometerPauseListener != null) {
            switch (type) {
                case PAUSE:
                    mOnChronometerPauseListener.OnPause(this);
                    break;
                case UNPAUSE:
                    mOnChronometerPauseListener.OnUnpause(this);
                    break;
            }
        }

    }

    void dispatchTick() {
        if (mOnChronometerTickListener != null) {
            mOnChronometerTickListener.onTick(this);
        }
    }
}
