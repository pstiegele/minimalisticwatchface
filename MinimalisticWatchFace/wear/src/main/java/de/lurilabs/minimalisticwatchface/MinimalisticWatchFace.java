
package de.lurilabs.minimalisticwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MinimalisticWatchFace extends CanvasWatchFaceService {

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(40);
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MinimalisticWatchFace.Engine> mWeakReference;

        public EngineHandler(MinimalisticWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MinimalisticWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        Calendar mCalendar;

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mTickPaint;
        Paint mRTickPaint;
        Paint mCirclePaint;
        Paint mDatePaint;
        Paint mDayPaint;

        Bitmap originalHourHand;
        Bitmap originalMinHand;
        Bitmap hourHand;
        Bitmap minHand;

        Typeface dayDateText;

        int width;
        int height;

        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MinimalisticWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mTime = new Time();
        }
        public void onSurfaceChanged(SurfaceHolder holder, int format, int mWidth, int mHeight){
            super.onSurfaceChanged(holder, format, width, height);

            width = mWidth;
            height = mHeight;

            originalHourHand = BitmapFactory.decodeResource(getResources(), R.drawable.stundenzeiger);
            originalMinHand = BitmapFactory.decodeResource(getResources(), R.drawable.minutenzeiger);

            int hourHandx = Math.round((float) 0.041*width);
            int hourHandy = Math.round((float) 0.205*width);
            int minHandx = Math.round((float) 0.035*width);
            int minHandy = Math.round((float) 0.322*width);

            hourHand = Bitmap.createScaledBitmap(originalHourHand, hourHandx, hourHandy, true);
            minHand = Bitmap.createScaledBitmap(originalMinHand, minHandx, minHandy, true);

            mHourPaint = new Paint();
            mHourPaint.setARGB(255, 255, 255, 255);
            mHourPaint.setStrokeWidth((float) 0.041*width);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);

            mMinutePaint = new Paint();
            mMinutePaint.setARGB(255, 255, 255, 255);
            mMinutePaint.setStrokeWidth((float) 0.035 * width);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondPaint = new Paint();
            mSecondPaint.setARGB(255, 255, 0, 0);
            mSecondPaint.setStrokeWidth((float) 0.008 * width);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);

            mTickPaint = new Paint();
            mTickPaint.setARGB(255, 255, 255, 255);
            mTickPaint.setStrokeWidth((float) 0.008 * width);
            mTickPaint.setAntiAlias(true);

            mRTickPaint = new Paint();
            mRTickPaint.setARGB(255, 255, 0, 0);
            mRTickPaint.setStrokeWidth((float) 0.02*width);
            mRTickPaint.setAntiAlias(true);

            mCirclePaint = new Paint();
            mCirclePaint.setARGB(255, 255, 0, 0);
            mCirclePaint.setStrokeWidth((float) 0.008*width);
            mCirclePaint.setAntiAlias(true);

            dayDateText = Typeface.createFromAsset(getAssets(),"EuroCaps.ttf");
            mDatePaint = new Paint();
            mDatePaint.setTypeface(dayDateText);
            mDatePaint.setTextSize((float) 0.11 * width);
            mDatePaint.setARGB(255, 255, 0, 0);

            mDayPaint = new Paint();
            mDayPaint.setTypeface(dayDateText);
            mDayPaint.setTextSize((float) 0.08*width);
            mDayPaint.setARGB(255, 255, 0, 0);

            mCalendar = Calendar.getInstance();


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    boolean antiAlias = !inAmbientMode;
                    mHourPaint.setAntiAlias(antiAlias);
                    mMinutePaint.setAntiAlias(antiAlias);
                    mSecondPaint.setAntiAlias(antiAlias);
                    mTickPaint.setAntiAlias(antiAlias);
                    mRTickPaint.setAntiAlias(antiAlias);
                }
                invalidate();
            }
             updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MinimalisticWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;

                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            //set Tickbreite ?
            //TODO: Auslagern
            int Ticks = Math.round(0.068f*width);
            int RTicks = Math.round(0.098f*width);
            canvas.drawColor(Color.BLACK);
            //TODO: Auslagern
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Draw the ticks.
            float innerTickRadius;
            float outerTickRadius;
            Paint paint;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                //skips first iteration to not draw paint on 12h position
                if (tickIndex == 0) {
                    continue;
                }
                //if modulo 3 is zero its a quarter position: paint big and red
                if (tickIndex % 3 == 0) {
                    innerTickRadius = centerX - RTicks;
                    outerTickRadius = centerX;
                    paint = mRTickPaint;
                } else {
                    innerTickRadius = centerX - Ticks;
                    outerTickRadius = centerX;
                    paint = mTickPaint;
                }
                float tickRot = (float) ((tickIndex * 45) * Math.PI / 270);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(centerX + innerX, centerY + innerY,
                        centerX + outerX, centerY + outerY, paint);
            }

            //12 Uhr Ticks (l. und r.)
            canvas.drawLine(centerX - 0.017f*width, 0, centerX - 0.017f*width, RTicks, mRTickPaint);
            canvas.drawLine(centerX + 0.017f*width, 0, centerX + 0.017f*width, RTicks, mRTickPaint);

            //Calendar aktualisieren
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            //
            float secondsmilli = (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            float secRot = (secondsmilli) / 30f * (float) Math.PI;


            float minDegree = (mTime.minute + (mTime.second/60f)) * 6f;
            float hourDegree = (mTime.hour + (mTime.minute/60f)) * 30f;
            float secLength = 0.488f*width;
            Matrix hourMatrix = new Matrix();
            hourMatrix.setRotate(hourDegree, hourHand.getWidth()/2, hourHand.getHeight() + 0.082f*width);
            hourMatrix.postTranslate(centerX-hourHand.getWidth()/2,centerY-(hourHand.getHeight() + 0.082f*width));

            Matrix minMatrix = new Matrix();
            minMatrix.setRotate(minDegree, minHand.getWidth() / 2, minHand.getHeight() + 0.096f * width);
            minMatrix.postTranslate(centerX - minHand.getWidth() / 2, centerY - (minHand.getHeight() + 0.096f * width));



            //SDF um Wochentag und Tag zu formatieren
            SimpleDateFormat dateSDF = new SimpleDateFormat("dd", Locale.US);
            SimpleDateFormat daySDF = new SimpleDateFormat("EEEE", Locale.US);

            //Wochentag zeichnen
            float textWidthDate = mDatePaint.measureText(dateSDF.format(mCalendar.getTime()));
            System.out.print(String.valueOf(textWidthDate));
            canvas.drawText(dateSDF.format(mCalendar.getTime()), centerX-textWidthDate/2, centerY + 0.6f*centerY, mDatePaint);

            //Tag zeichnen
            float textWidthDay = mDayPaint.measureText(daySDF.format(mCalendar.getTime()));
            canvas.drawText(daySDF.format(mCalendar.getTime()), centerX-textWidthDay/2, centerY - 0.45f*centerY, mDayPaint);

            if (!isInAmbientMode()) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mSecondPaint);
                canvas.drawCircle(centerX, centerY, 0.016f*width, mCirclePaint);
            }
            canvas.drawBitmap(hourHand,hourMatrix,mHourPaint);
            canvas.drawBitmap(minHand, minMatrix, mMinutePaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MinimalisticWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MinimalisticWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
