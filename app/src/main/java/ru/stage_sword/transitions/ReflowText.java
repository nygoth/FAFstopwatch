/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.stage_sword.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.transition.Transition;
import android.support.transition.TransitionValues;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.stage_sword.fafstopwatch.R;

/**
 * A transition for repositioning text. This will animate changes in text size and position,
 * re-flowing line breaks as necessary.
 * <p>
 * Strongly recommended to use a curved {@code pathMotion} for a more natural transition.
 */
public class ReflowText extends Transition {

    private static final String TAG = "ReflowText";

//    private static final String EXTRA_REFLOW_DATA = "EXTRA_REFLOW_DATA";
    private static final String PROPNAME_DATA = "plaid:reflowtext:data";
    private static final String PROPNAME_TEXT_SIZE = "plaid:reflowtext:textsize";
    private static final String PROPNAME_BOUNDS = "plaid:reflowtext:bounds";
    private static final String[] PROPERTIES = { PROPNAME_TEXT_SIZE, PROPNAME_BOUNDS };
    private static final int TRANSPARENT = 0;
    private static final int OPAQUE = 255;
    private static final int OPACITY_MID_TRANSITION = (int) (0.8f * OPAQUE);
//    private static final float STAGGER_DELAY = 0.8f;

    private int velocity      = 700;         // pixels per second
    private long minDuration  = 10;     // ms
    private long maxDuration  = 1000;     // ms
    private long staggerDelay = 40;     // ms
    private long _duration;
    // this is hack for preventing view from drawing briefly at the end of the transition :(
    private boolean freezeFrame = false;

    public ReflowText(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ReflowText);
        velocity = a.getDimensionPixelSize(R.styleable.ReflowText_velocity, velocity);
        minDuration = a.getInt(R.styleable.ReflowText_minDuration, (int) minDuration);
        maxDuration = a.getInt(R.styleable.ReflowText_maxDuration, (int) maxDuration);
        staggerDelay = a.getInt(R.styleable.ReflowText_staggerDelay, (int) staggerDelay);
        freezeFrame = a.getBoolean(R.styleable.ReflowText_freezeFrame, false);
        a.recycle();
    }

    private ReflowData getReflowData(@NonNull View view) {
        if(view instanceof TextView) {
            ReflowData reflowData = new ReflowData(new ReflowableTextView((TextView)view));

            view.setTag(R.id.tag_reflow_data, null);
            return reflowData;
        }
        return null;
    }

    private void captureValues(TransitionValues transitionValues) {
        ReflowData reflowData = getReflowData(transitionValues.view);
        transitionValues.values.put(PROPNAME_DATA, reflowData);
        if (reflowData != null) {
            // add these props to the map separately (even though they are captured in the reflow
            // data) to use only them to determine whether to create an animation i.e. only
            // animate if text size or bounds have changed (see #getTransitionProperties())
            transitionValues.values.put(PROPNAME_TEXT_SIZE, reflowData.textSize);
            transitionValues.values.put(PROPNAME_BOUNDS,    reflowData.bounds);
        }
    }

    @NonNull
    @Override
    public Transition setDuration(long duration) {
        /* don't call super as we want to handle _duration ourselves */
        _duration = duration;
        return this;
    }

    @Override
    public void captureStartValues(@NonNull TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(@NonNull TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public String[] getTransitionProperties() {
        return PROPERTIES;
    }

    @Override
    public Animator createAnimator(
            @NonNull ViewGroup sceneRoot,
            TransitionValues startValues,
            TransitionValues endValues) {

        if (startValues == null || endValues == null) return null;

        final TextView startView = (TextView)startValues.view;
        final TextView endView = (TextView)endValues.view;
        AnimatorSet transition = new AnimatorSet();

        ReflowData startData = (ReflowData) startValues.values.get(PROPNAME_DATA);
        ReflowData endData   = (ReflowData) endValues  .values.get(PROPNAME_DATA);

//        _duration = calculateDuration(startData.bounds, endData.bounds);

        final Bitmap startText = startView.getVisibility() == View.VISIBLE ? createBitmap(startData, startView) : null;
        final Bitmap endText   = endView.getVisibility()   == View.VISIBLE ? createBitmap(endData,   endView)   : null;

        // temporarily turn off clipping so we can draw outside of our bounds don't draw
        endView.setWillNotDraw(true);
        ((ViewGroup) endView.getParent()).setClipChildren(false);
        transition.playTogether(createDrawableAnimator(startView, endView, startData, endData, startText, endText));

        if (!freezeFrame) {
            transition.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // clean up
                    endView.setWillNotDraw(false);
                    endView.getOverlay().clear();
                    ((ViewGroup) endView.getParent()).setClipChildren(true);

                    if(startText != null) startText.recycle();
                    if(endText   != null) endText  .recycle();
                }
            });
        }
        return transition;
    }

    /**
     * Create Animators to transition bitmap from start to end size.
     */
    @NonNull
    private List<Animator> createDrawableAnimator(
            TextView startView, TextView endView,
            ReflowData startData,
            ReflowData endData,
            Bitmap startText,
            Bitmap endText) {

        List<Animator> animators = new ArrayList<>();
        long startDelay = 0L;
        int startVisibility = startView.getVisibility();
        int endVisibility   = endView.getVisibility();

        // TODO get interpolator from transition setting
        DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

        // create & position the drawable which displays the run; add it to the overlay.
        SwitchDrawable drawable = new SwitchDrawable(startText, startData.bounds, startData.textSize,
                                                     endText,   endData.bounds,   endData.textSize);
        drawable.setBounds(startData.bounds);
        endView.getOverlay().add(drawable);

        PropertyValuesHolder width  = PropertyValuesHolder.ofInt(SwitchDrawable.WIDTH,  startData.bounds.width(),  endData.bounds.width());
        PropertyValuesHolder height = PropertyValuesHolder.ofInt(SwitchDrawable.HEIGHT, startData.bounds.height(), endData.bounds.height());

        // the progress property drives the switching behaviour
        PropertyValuesHolder progress = PropertyValuesHolder.ofFloat(SwitchDrawable.PROGRESS, 0f, 1f);

        Animator runAnim = ObjectAnimator.ofPropertyValuesHolder(drawable, width, height, progress);

        runAnim.setStartDelay(startDelay);
        long animDuration = Math.max(minDuration, _duration - (startDelay / 2));
        runAnim.setDuration(animDuration);
        animators.add(runAnim);

        if (startVisibility != endVisibility) {
            // if run is appearing/disappearing then fade it in/out
            ObjectAnimator fade = ObjectAnimator.ofInt(
                    drawable,
                    SwitchDrawable.ALPHA,
                    startVisibility == View.VISIBLE ? OPAQUE : TRANSPARENT,
                    endVisibility   == View.VISIBLE ? OPAQUE : TRANSPARENT);

            fade.setDuration((_duration + startDelay) / 2);

            if (startVisibility != View.VISIBLE) {
                drawable.setAlpha(TRANSPARENT);
                fade.setStartDelay((_duration + startDelay) / 2);
            } else {
                fade.setStartDelay(startDelay);
            }
            animators.add(fade);
        } else {
            // slightly fade during transition to minimize movement
            ObjectAnimator fade = ObjectAnimator.ofInt(
                    drawable,
                    SwitchDrawable.ALPHA,
                    OPAQUE, OPACITY_MID_TRANSITION, OPAQUE);

            fade.setStartDelay(startDelay);
            fade.setDuration(_duration + startDelay);
            fade.setInterpolator(decelerateInterpolator);
            animators.add(fade);
        }

        return animators;
    }

    private Bitmap createBitmap(@NonNull ReflowData data, @NonNull  View view) {
        int left = view.getLeft();
        int top = view.getTop();
        int right = view.getRight();
        int bottom = view.getBottom();

        Bitmap bitmap = Bitmap.createBitmap(data.bounds.width(), data.bounds.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.layout(data.bounds.left,data.bounds.top,data.bounds.right,data.bounds.bottom);
        view.draw(canvas);
        view.layout(left,top,right,bottom);

        return bitmap;
    }

    /**
     * Holds all data needed to describe a block of text i.e. to be able to re-create the
     * {@link Layout}.
     */
    private static class ReflowData implements Parcelable {

        final String text;
        final float textSize;
        final @ColorInt int textColor;
        final Rect bounds;
        final Point textPosition;
        final int textWidth;

        ReflowData(@NonNull Reflowable reflowable) {
            text = reflowable.getText();
            textSize = reflowable.getTextSize();
            textColor = reflowable.getTextColor();
            final View view = reflowable.getView();
            int[] loc = new int[2];
            view.getLocationInWindow(loc);
            bounds = new Rect(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
            textPosition = reflowable.getTextPosition();
            textWidth = reflowable.getTextWidth();
        }

        ReflowData(Parcel in) {
            text = in.readString();
            textSize = in.readFloat();
            textColor = in.readInt();
            bounds = (Rect) in.readValue(Rect.class.getClassLoader());
            textPosition = (Point) in.readValue(Point.class.getClassLoader());
            textWidth = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(text);
            dest.writeFloat(textSize);
            dest.writeInt(textColor);
            dest.writeValue(bounds);
            dest.writeValue(textPosition);
            dest.writeInt(textWidth);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<ReflowData> CREATOR = new Parcelable.Creator<ReflowData>() {
            @Override
            public ReflowData createFromParcel(Parcel in) {
                return new ReflowData(in);
            }

            @Override
            public ReflowData[] newArray(int size) {
                return new ReflowData[size];
            }
        };

    }

    /**
     * A drawable which shows (a portion of) one of two given bitmaps, switching between them once
     * a progress property passes a threshold.
     * <p>
     * This is helpful when animating text size change as small text scaled up is blurry but larger
     * text scaled down has different kerning. Instead we use images of both states and switch
     * during the transition. We use images as animating text size thrashes the font cache.
     */
    private static class SwitchDrawable extends Drawable {

        static final Property<SwitchDrawable, Point> TOP_LEFT =
                new Property<SwitchDrawable, Point>(Point.class, "topLeft") {
                    @Override
                    public void set(SwitchDrawable drawable, Point topLeft) {
                        drawable.setTopLeft(topLeft);
                    }

                    @Override
                    public Point get(SwitchDrawable drawable) {
                        return drawable.getTopLeft();
                    }
                };

        static final Property<SwitchDrawable, Integer> TOP =
                new Property<SwitchDrawable, Integer>(Integer.class, "top") {
                    @Override
                    public void set(SwitchDrawable drawable, Integer top) {
                        drawable.setTop(top);
                    }

                    @Override
                    public Integer get(SwitchDrawable drawable) {
                        return drawable.getTop();
                    }
                };

        static final Property<SwitchDrawable, Integer> LEFT =
                new Property<SwitchDrawable, Integer>(Integer.class, "left") {
                    @Override
                    public void set(SwitchDrawable drawable, Integer left) {
                        drawable.setLeft(left);
                    }

                    @Override
                    public Integer get(SwitchDrawable drawable) {
                        return drawable.getLeft();
                    }
                };

        static final Property<SwitchDrawable, Integer> WIDTH =
                new Property<SwitchDrawable, Integer>(Integer.class, "width") {
                    @Override
                    public void set(SwitchDrawable drawable, Integer width) {
                        drawable.setWidth(width);
                    }

                    @Override
                    public Integer get(SwitchDrawable drawable) {
                        return drawable.getWidth();
                    }
                };

        static final Property<SwitchDrawable, Integer> HEIGHT =
                new Property<SwitchDrawable, Integer>(Integer.class, "height") {
                    @Override
                    public void set(SwitchDrawable drawable, Integer height) {
                        drawable.setHeight(height);
                    }

                    @Override
                    public Integer get(SwitchDrawable drawable) {
                        return drawable.getHeight();
                    }
                };

        static final Property<SwitchDrawable, Integer> ALPHA =
                new Property<SwitchDrawable, Integer>(Integer.class, "alpha") {
                    @Override
                    public void set(SwitchDrawable drawable, Integer alpha) {
                        drawable.setAlpha(alpha);
                    }

                    @Override
                    public Integer get(SwitchDrawable drawable) {
                        return drawable.getAlpha();
                    }
                };

        static final Property<SwitchDrawable, Float> PROGRESS =
                new Property<SwitchDrawable, Float>(Float.class, "progress") {
                    @Override
                    public void set(SwitchDrawable drawable, Float progress) {
                        drawable.setProgress(progress);
                    }

                    @Override
                    public Float get(SwitchDrawable drawable) {
                        return 0f;
                    }
                };

        private final Paint paint;
        private final float switchThreshold;
        private Bitmap currentBitmap;
        private final Bitmap endBitmap;
        private final Rect endBitmapSrcBounds;
        private boolean hasSwitched = false;
        private Point topLeft;
        private int width, height;

        SwitchDrawable(
                @NonNull Bitmap startBitmap,
                @NonNull Rect startBitmapSrcBounds,
                float startFontSize,
                @NonNull Bitmap endBitmap,
                @NonNull Rect endBitmapSrcBounds,
                float endFontSize) {
            currentBitmap = startBitmap;
            this.endBitmap = endBitmap;
            this.endBitmapSrcBounds = endBitmapSrcBounds;
            switchThreshold = startFontSize / (startFontSize + endFontSize);
            paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            topLeft = new Point(0,0);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if(currentBitmap != null) canvas.drawBitmap(currentBitmap, null, getBounds(), paint);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        void setProgress(float progress) {
            if (!hasSwitched && progress >= switchThreshold) {
                currentBitmap = endBitmap;
                hasSwitched = true;
            }
        }
        @Override
        public int getAlpha() {
            return paint.getAlpha();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public ColorFilter getColorFilter() {
            return paint.getColorFilter();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }


        Point getTopLeft() {
            return topLeft;
        }

        int getTop() { return topLeft.y; }
        int getLeft() { return topLeft.x; }

        void setTopLeft(Point topLeft) {
            this.topLeft = topLeft;
            updateBounds();
        }

        void setTop(int top) {
            this.topLeft.y = top;
            updateBounds();
        }

        void setLeft(int left) {
            this.topLeft.x = left;
            updateBounds();
        }

        int getWidth() {
            return width;
        }

        void setWidth(int width) {
            this.width = width;
            updateBounds();
        }

        int getHeight() {
            return height;
        }

        void setHeight(int height) {
            this.height = height;
            updateBounds();
        }

        private void updateBounds() {
            setBounds(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + height);
        }
    }

    /**
     * Interface describing a view which supports re-flowing i.e. it exposes enough information to
     * construct a {@link ReflowData} object;
     */
    public interface Reflowable<T extends View> {

        T getView();
        String getText();
        Point getTextPosition();
        int getTextWidth();
        float getTextSize();
        @ColorInt int getTextColor();
    }

    /**
     * Wraps a {@link TextView} and implements {@link Reflowable}.
     */
    public static class ReflowableTextView implements Reflowable<TextView> {

        private final TextView textView;

        public ReflowableTextView(TextView textView) {
            this.textView = textView;
        }

        @Override
        public TextView getView() {
            return textView;
        }

        @Override
        public String getText() {
            return textView.getText().toString();
        }

        @Override
        public Point getTextPosition() {
            return new Point(textView.getCompoundPaddingLeft(), textView.getCompoundPaddingTop());
        }

        @Override
        public int getTextWidth() {
            return textView.getWidth() - textView.getCompoundPaddingLeft() - textView.getCompoundPaddingRight();
        }

        @Override
        public float getTextSize() {
            return textView.getTextSize();
        }

        @Override
        public int getTextColor() {
            return textView.getCurrentTextColor();
        }
    }
}
