package com.learn.heddy.sunshinewearever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
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

import static android.graphics.Color.parseColor;

/*
 * This class is modeled after Sample project 'WatchFaces'
 * Most closely 'DigitalWatchFaceService'
 * Added Sunshine Weather data components sent from the mobile app
 *
 * Also implemented simpler color mode if the watch is in ambient-mode.
 * Sunshine blue's are switched to black or gray.
 * Weather icon image is also turned to gray scale using the background image lesson video.
 *
 */
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
     * Name of the default ambient mode for Calendar and Min temperature texts
     */
    public static final String COLOR_NAME_DEFAULT_AMBIENT_GRAY = "Gray";
    public static final int COLOR_VALUE_DEFAULT__AMBIENT_GRAY = parseColor(COLOR_NAME_DEFAULT_AMBIENT_GRAY);

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

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

        private final String COLON_STRING = ":";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Calendar mCalendar;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /** used for blinking colon flag */
        boolean mShouldDrawColons;

        /** Graphics variables */
        float mXOffset;
        float mYOffset;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        float mColonWidth;

        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mColonPaint;
        Paint mCenterLine;
        Paint mDatePaint;
        Paint mWeatherDataPaint;
        Paint mWeatherDataPaintMuted;

        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;

        /* Offset variables that increments at runtime */
        private float mHourXoffset;
        private float mWeatherDataXoffset;
        private float weatherCenterBaseY;
        private float weatherY10thUnit;
        private float weatherY20thUnit;
        private float mDecoDeviderLineHalfLength;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

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

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.colorPrimary));

            mDatePaint = createTextPaint(resources.getColor(R.color.colorPrimaryLight));

            mHourPaint = createTextPaint(Color.WHITE);
            mMinutePaint = createTextPaint(Color.WHITE);

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(Color.WHITE);
            mColonPaint = createTextPaint(Color.WHITE);
            mCenterLine = createLinePaint(resources.getColor(R.color.colorPrimaryLight));     //mInteractiveMinuteDigitsColor);

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
//            initializeWeatherGraphics();

        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE, MMM d YYYY", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
        }

//        private void initializeWeatherGraphics() {
////            if (Log.isLoggable(TAG, Log.DEBUG)) {
//            Log.d(TAG, "initializeWeatherGraphics()");
////            }
//            Resources resources = SunshineWatchFaceService.this.getResources();
//            mWeatherDataPaint = new Paint();
//            mWeatherDataPaint.setColor(Color.WHITE);
//            mWeatherDataPaint.setTextSize(resources.getDimension(R.dimen.weather_data_size));//(WEATHER_DATA_TEXT_SIZE);
////            mWeatherDataPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
//            mWeatherDataPaint.setTypeface(NORMAL_TYPEFACE);
////            mWeatherDataPaint.setTextAlign(Paint.Align.CENTER);
//            mWeatherDataPaint.setAntiAlias(true);
//
//            mWeatherDataPaintMuted = new Paint();
//            mWeatherDataPaintMuted.setColor(resources.getColor(R.color.colorPrimaryLight));
//            mWeatherDataPaintMuted.setTextSize(WEATHER_DATA_TEXT_SIZE);
////            mWeatherDataPaintMuted.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
//            mWeatherDataPaintMuted.setTypeface(NORMAL_TYPEFACE);
////            mWeatherDataPaintMuted.setTextAlign(Paint.Align.CENTER);
//            mWeatherDataPaintMuted.setAntiAlias(true);
//
//        }

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
            paint.setStrokeWidth(2f);
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
                invalidate(); //@@@??? sample Digital WF does not do this , where is this coming from??
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

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float weatherTextSize = resources.getDimension(isRound
                    ? R.dimen.weather_text_size_round : R.dimen.weather_text_size);

            // Time and Calendar
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size)); // Date has the same size for both insets
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mColonWidth = mColonPaint.measureText(COLON_STRING);

            // Sunshine weather
            mWeatherDataPaint = createTextPaint(Color.WHITE);
            mWeatherDataPaint.setTextSize(weatherTextSize);
            mWeatherDataPaintMuted = createTextPaint(resources.getColor(R.color.colorPrimaryLight));
            mWeatherDataPaintMuted.setTextSize(weatherTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            // For Sunshine we're always using Light typefaces - so comment this out
            /*
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
            */

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

            if (isInAmbientMode()){
                mDatePaint.setColor(COLOR_VALUE_DEFAULT__AMBIENT_GRAY);
                mCenterLine.setColor(COLOR_VALUE_DEFAULT__AMBIENT_GRAY);
                mWeatherDataPaintMuted.setColor(COLOR_VALUE_DEFAULT__AMBIENT_GRAY);
            }
            else {
                Resources resources = SunshineWatchFaceService.this.getResources();
                int ligtBlue = resources.getColor(R.color.colorPrimaryLight);
                mCenterLine.setColor(ligtBlue);
                mDatePaint.setColor(ligtBlue);
                mWeatherDataPaintMuted.setColor(ligtBlue);
            }

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;  // this means No anti alias during Ambient mode to save battery
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
//            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
//            }
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw the time with colon in-between
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY)); // Sunshine watchface uses 24hour format
            float hlen = mHourPaint.measureText(hourString);

            float clen = mColonWidth;

            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            float mlen = mMinutePaint.measureText(minuteString);

            float yLine1 = mHeight/4 + weatherY10thUnit;    // Y offset for the time
            float yLine2 = weatherCenterBaseY - weatherY10thUnit;   // Y offset for the calendar

            // First X-offset for the time
            mHourXoffset = (mWidth - (hlen+clen+mlen))/2;
            canvas.drawText(hourString, mHourXoffset, yLine1, mHourPaint);

            // increment for the colon
            mHourXoffset += hlen;
            // Draw if Ambient mode where static colon is displayed or if the flag is true
            if (isInAmbientMode() || mShouldDrawColons){
                canvas.drawText(COLON_STRING, mHourXoffset, yLine1, mHourPaint);
            }

            // increment for the minute finally!
            mHourXoffset += clen;
            canvas.drawText(minuteString, mHourXoffset, yLine1, mHourPaint);

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                // Day of week
                String dayString = mDayOfWeekFormat.format(mDate);
                if (dayString!=null){
                    dayString = dayString.toUpperCase();
                }

                mWeatherDataXoffset = (mWidth - mDatePaint.measureText(dayString))/2;
                canvas.drawText(dayString, mWeatherDataXoffset, yLine2, mDatePaint);
            }

            canvas.drawLine(mCenterX-mDecoDeviderLineHalfLength, weatherCenterBaseY,
                            mCenterX+mDecoDeviderLineHalfLength, weatherCenterBaseY, mCenterLine);
            drawSunshineData(canvas);
        }

        private void drawSunshineData(Canvas canvas) {
            Bitmap weatherImage=null;
            Bitmap scaledWeatherImage = null;
            String highOnly = "High";
            String lowOnly = "Low";

            int h;
            int w;
            float iLen = 0f;
            float spaceLen = weatherY20thUnit;  // My design decision of gaps that look nice for the weather data display
            float yLine3 = weatherCenterBaseY + weatherY10thUnit * 2;

            SunshineWatchFaceUtil.TodayData sunshineData = SunshineWatchFaceUtil.fetchSunshineData(getApplicationContext());
            if (sunshineData!=null) {
                weatherImage = sunshineData.getWeatherImage();
                if (weatherImage!=null){
                    float resolutionFactor = ((float) mWidth)/280f; // My design decision was to base the 280dp screen; e.g. 480f/ 280f
                    float scale = resolutionFactor * .5f;           // My decision on icon image scale - half of the Phone icon, so .5f

                    w = (int) (weatherImage.getWidth() * scale);
                    h = (int) (weatherImage.getHeight() * scale);

                    scaledWeatherImage = weatherImage.createScaledBitmap(weatherImage, w, h, false);
                    iLen = scaledWeatherImage.getWidth();
                }

                highOnly = sunshineData.getHighOnly()!=null? sunshineData.getHighOnly(): "Today's High";
                lowOnly = sunshineData.getLowOnly()!=null? sunshineData.getLowOnly(): " Low";
           }

            float allWeatherLen = iLen
                    + mWeatherDataPaint.measureText(highOnly)
                    + mWeatherDataPaint.measureText(lowOnly);


            mWeatherDataXoffset = (mWidth-allWeatherLen)/2 + spaceLen;

            Log.d(TAG, "iLen: "+ iLen + " highOnly "+mWeatherDataPaint.measureText(highOnly)+" xOffset " + mWeatherDataXoffset);

            if (scaledWeatherImage!=null) {
                if (isInAmbientMode()){
                    Bitmap grayBitmap = buildGrayscaleBitmap(scaledWeatherImage);
                    canvas.drawBitmap(grayBitmap, mWeatherDataXoffset, weatherCenterBaseY + weatherY20thUnit, null);

                } else {
                    canvas.drawBitmap(scaledWeatherImage, mWeatherDataXoffset, weatherCenterBaseY + weatherY20thUnit, null);
                }

                mWeatherDataXoffset = mWeatherDataXoffset + spaceLen/2 + scaledWeatherImage.getWidth();
            }

            canvas.drawText(highOnly, mWeatherDataXoffset, yLine3, mWeatherDataPaint);
            canvas.drawText(lowOnly,
                    mWeatherDataXoffset + spaceLen + mWeatherDataPaint.measureText(highOnly)/2,
                    yLine3,
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
                long delayMs = mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        /**
         *  VERY IMPORTANT Callback where we are using the realtime width and height
         *  to set the base mHeight, mWidth and mCenterX that many offsets are calculated throughout
         *  onDraw() and drawSunshineData()
         *
         * @param holder
         * @param format
         * @param width
         * @param height
         */
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mHeight = height;
            mWidth = width;

            mCenterX = holder.getSurfaceFrame().exactCenterX();
            mCenterY = holder.getSurfaceFrame().exactCenterY();

            /* Useful grid units */
            weatherY10thUnit = height/10f;
            weatherY20thUnit = height/20f;
            weatherCenterBaseY = mCenterY + weatherY20thUnit;

            /* Used to calculate x offset of the decoration horizontal divider line
            *  We'd like the length to be 0.18 of the width, therefore x-offset is the half of that 0.09f.
            * */
            mDecoDeviderLineHalfLength = width * 0.09f;
        }

        private Bitmap buildGrayscaleBitmap(Bitmap colorBitmap) {
            Bitmap grayBitmap = Bitmap.createBitmap(
                    colorBitmap.getWidth(),
                    colorBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(grayBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(colorBitmap, 0, 0, grayPaint);
            return grayBitmap;
        }
    }
/* *
Note to the Reviewer

If I used the latest, I am getting connection error on the wearable data listener.
compile 'com.google.android.gms:play-services-wearable:10.2.0'

SunshineDataListener: Failed to connect to GoogleApiClient.
*/
}
