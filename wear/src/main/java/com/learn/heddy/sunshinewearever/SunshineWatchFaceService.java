/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.learn.heddy.sunshinewearever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = "SunshineWFService";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create("sans-serif-light", Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    // Today's weather data variables
    private static SunshineWatchFaceUtil mSunshineUtil;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
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
        private static final float WEATHER_DATA_TEXT_SIZE = 38f;
        private final String COLON_STRING = ":";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /* Additional variables from the WatchFace sample project */
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mColonPaint;
        float mColonWidth;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mLineHeight;

        /* Today's weather data graphics */
        // Sunshine complications
        Paint mCenterLine;
        Paint mDatePaint;
        int mWeatherId;
        int mTodayHigh;
        int mTodayLow;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        private Paint mWeatherDataPaint;
        private Paint mWeatherDataPaintMuted;
        private int mWeatherDataY;
        private int mHorizontalMargin = 15;  //TODO: add to dimens

        /* test better x and y */
        private float mHourXoffset;
        private float mDateXoffset;
        private float mWeatherDataXoffset;
        private float m_yLine3;
        private float m_ySixteenth;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();

            /* Values from WatchFace sample project */
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mHourPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mMinutePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mCenterLine = createLinePaint(resources.getColor(R.color.digital_text));     //mInteractiveMinuteDigitsColor);

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
            initializeWeatherGraphics();

        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE, MMM d YYYY", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
        }

        private void initializeWeatherGraphics() {
//            if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "initializeWeatherGraphics()");
//            }

            mWeatherDataPaint = new Paint();
            mWeatherDataPaint.setColor(Color.MAGENTA);
            mWeatherDataPaint.setTextSize(WEATHER_DATA_TEXT_SIZE);
//            mWeatherDataPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            mWeatherDataPaint.setTypeface(NORMAL_TYPEFACE);
//            mWeatherDataPaint.setTextAlign(Paint.Align.CENTER);
            mWeatherDataPaint.setAntiAlias(true);

            mWeatherDataPaintMuted = new Paint();
            mWeatherDataPaintMuted.setColor(Color.argb(180, 255, 0, 255));
            mWeatherDataPaintMuted.setTextSize(WEATHER_DATA_TEXT_SIZE);
//            mWeatherDataPaintMuted.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            mWeatherDataPaintMuted.setTypeface(NORMAL_TYPEFACE);
//            mWeatherDataPaintMuted.setTextAlign(Paint.Align.CENTER);
            mWeatherDataPaintMuted.setAntiAlias(true);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            //paint.setTextSize(40f); //72f);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createLinePaint(int defaultInteractiveColor) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(4f);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
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
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//            float textSize = resources.getDimension(isRound
//                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            /* */
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size)); // Date has the same size for both insets
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
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
                    mDatePaint.setAntiAlias(antiAlias);
                    mHourPaint.setAntiAlias(antiAlias);
                    mMinutePaint.setAntiAlias(antiAlias);
                    mColonPaint.setAntiAlias(antiAlias);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            mHeight = bounds.height();
            mCenterY = mHeight/2f;
            mWidth = bounds.width();
            mCenterX = mWidth/2f;

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = true;

// START-auto-generated format           String text = mAmbient
//                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
//                    mCalendar.get(Calendar.MINUTE))
//                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
//                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
// END-           canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }

            float hlen = mHourPaint.measureText(hourString);
            float clen = mColonWidth;
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            float mlen = mMinutePaint.measureText(minuteString);
            m_ySixteenth = mHeight*1/16;
            float yLine1 = mHeight/4+m_ySixteenth;
            float yLine2 = mHeight*3/8+m_ySixteenth;
            m_yLine3 = mHeight*3/4; //+ySixteenth;
            String allTime = hourString.concat(COLON_STRING).concat(minuteString);

            mHourXoffset = (mWidth - (hlen+clen+mlen))/2;

            Log.d(TAG, "mWidth: "+ mWidth + " hour_col_min "+(hlen+clen+mlen)+" mHourXoffset" + mHourXoffset);

//            canvas.drawText(hourString, x, mYOffset, mHourPaint);       //mHourPaint);
//            canvas.drawText(hourString, mHourXoffset, yLine1, mHourPaint);
            canvas.drawText(allTime, mHourXoffset, yLine1, mHourPaint);

//            x += mHourPaint.measureText(hourString);
//            float colonXoffset = mHourXoffset+hlen;

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
//            if (isInAmbientMode()) {                                   // || mMute || mShouldDrawColons) {
//                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
//            }

//            canvas.drawText(COLON_STRING, colonXoffset, yLine1, mColonPaint);
//
//            x += mColonWidth;
//            float minuteXoffset = colonXoffset + mColonWidth;

            // Draw the minutes.
//            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
//            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);          //mMinutePaint);
//            canvas.drawText(minuteString, minuteXoffset, yLine1, mMinutePaint);
//            x += mMinutePaint.measureText(minuteString);

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                // Day of week
                String dayString = mDayOfWeekFormat.format(mDate);

                mWeatherDataXoffset = (mWidth - mDatePaint.measureText(dayString))/2;
                canvas.drawText(
                        dayString,
//                        mXOffset, mYOffset + mLineHeight, mDatePaint);                   //mDatePaint);
                        mWeatherDataXoffset, yLine2, mDatePaint);
            }

            // horizonatal line separator
//            mHeight = bounds.height();
//            mCenterY = mHeight/2f;
//            mWidth = bounds.width();
//            mCenterX = mWidth/2f;
            float halfLength = 50f;
            canvas.drawLine(mCenterX-halfLength, mCenterY, mCenterX+halfLength, mCenterY, mCenterLine);

            drawSunshineData(canvas, now);
        }

        private void drawSunshineData(Canvas canvas, long currentTimeMillis) {
            Log.d(TAG, "drawSunshineData()");

            Bitmap weatherImage=null;
            Bitmap scaledWeatherImage = null;
            String highOnly = "High";
            String lowOnly = "Low";

            int h = 0;
            float iLen = 0f;
            float spaceLen = 12f;

            SunshineWatchFaceUtil.TodayData sunshineData = SunshineWatchFaceUtil.fetchSunshineData(getApplicationContext());
            if (sunshineData!=null) {
                weatherImage = sunshineData.getWeatherImage();
                if (weatherImage!=null){
                    int w = Math.round(weatherImage.getWidth()*.5f);
                    h = Math.round(weatherImage.getHeight()*.5f);

                    scaledWeatherImage = weatherImage.createScaledBitmap(weatherImage, w, h, false);
                    iLen = scaledWeatherImage.getWidth();
                }

                highOnly = sunshineData.getHighOnly()!=null? sunshineData.getHighOnly(): "#2: High";
                lowOnly = sunshineData.getLowOnly()!=null? sunshineData.getLowOnly(): "#2: Low";
           }

            int weatherDataX = 100;    // TO-DO: better adjust later!

            mWeatherDataY = mHorizontalMargin*2 + new Float(mCenterY).intValue();

//            float allWeatherLen = iLen
//                    + mWeatherDataPaint.measureText(highOnly)
//                    + mWeatherDataPaintMuted.measureText(lowOnly)
//                    ;

            float allWeatherLen = iLen
                    + mWeatherDataPaint.measureText(highOnly)
//                    + mWeatherDataPaintMuted.measureText(lowOnly)
                    + spaceLen*6
                    ;

            mWeatherDataXoffset = (mWidth-allWeatherLen)/2;
            Log.d(TAG, "iLen: "+ iLen + " highOnly "+mWeatherDataPaint.measureText(highOnly)+" xOffset " + mWeatherDataXoffset);

            if (scaledWeatherImage!=null) {
//                canvas.drawBitmap(scaledWeatherImage, weatherDataX, mWeatherDataY, null);
//                canvas.drawBitmap(scaledWeatherImage, mWeatherDataXoffset, m_yLine3, null);
                canvas.drawBitmap(scaledWeatherImage, mWeatherDataXoffset, mCenterY+m_ySixteenth, null);
                mWeatherDataXoffset = mWeatherDataXoffset + spaceLen + scaledWeatherImage.getWidth();
            }

            canvas.drawText(
                    highOnly,
//                    0,
//                    highOnly.length(),
                    mWeatherDataXoffset,
//                    mWeatherDataY += h/2,
                    m_yLine3,
                    mWeatherDataPaint);

            canvas.drawText(
                    lowOnly,
//                    0,
//                    lowOnly.length(),
//                    weatherDataX + 60,
//                    mWeatherDataY,
                    mWeatherDataXoffset + spaceLen*2 + mWeatherDataPaint.measureText(highOnly)/2,
                    m_yLine3,
                    mWeatherDataPaintMuted);
    }



        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
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
