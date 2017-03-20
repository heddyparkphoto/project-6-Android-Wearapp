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
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
//    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
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

        private static final float WEATHER_DATA_TEXT_SIZE = 54f;
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
        boolean mShouldDrawColons;

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

        /* new on 3/19 -- */
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
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

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

            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
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
//            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
//            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

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
                mWeatherDataPaintMuted.setColor(COLOR_VALUE_DEFAULT__AMBIENT_GRAY);
            }
            else {
                Resources resources = SunshineWatchFaceService.this.getResources();
                mDatePaint.setColor(resources.getColor(R.color.colorPrimaryLight));
                mWeatherDataPaintMuted.setColor(resources.getColor(R.color.colorPrimaryLight));
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
            //MARCH 20 TH FINAL CLEANUP-BEGIN
//
//            mHeight = bounds.height();
//            mCenterY = mHeight/2f;
//            mWidth = bounds.width();
//            mCenterX = mWidth/2f;
//
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
//            float yLine2 = mHeight*3/8+m_ySixteenth;
//            m_yLine3 = mHeight*3/4; //+ySixteenth;

            float yLine2 = weatherCenterBaseY - weatherY10thUnit;
            m_yLine3 = weatherCenterBaseY + weatherY10thUnit * 2;
            String allTime = hourString.concat(COLON_STRING).concat(minuteString);

            mHourXoffset = (mWidth - (hlen+clen+mlen))/2;   // Base x offset for the complete time

            canvas.drawText(hourString, mHourXoffset, yLine1, mHourPaint);
            // calculate the new X for the colon
            mHourXoffset += hlen;

            // blinking effect or if ambient mode where static colon is always drawn
            // NOTE: We only use the mHourPaint for the all time texts.
            if (isInAmbientMode() || mShouldDrawColons){
                canvas.drawText(COLON_STRING, mHourXoffset, yLine1, mHourPaint);
            }
            // calculate the new X for the minute finally!
            mHourXoffset += clen;
            canvas.drawText(minuteString, mHourXoffset, yLine1, mHourPaint);

//            canvas.drawText(hourString, x, mYOffset, mHourPaint);       //mHourPaint);
//            canvas.drawText(hourString, mHourXoffset, yLine1, mHourPaint);
            /* NO MORE STATIC COLONS!!!
            canvas.drawText(allTime, mHourXoffset, yLine1, mHourPaint);
            */
            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
//            if (isInAmbientMode()) {                                   // || mMute || mShouldDrawColons) {
//                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
//            }

//            canvas.drawText(COLON_STRIN`G, colonXoffset, yLine1, mColonPaint);
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
                if (dayString!=null){
                    dayString = dayString.toUpperCase();
                }

                mWeatherDataXoffset = (mWidth - mDatePaint.measureText(dayString))/2;
                canvas.drawText(dayString, mWeatherDataXoffset, yLine2, mDatePaint);
            }

            canvas.drawLine(mCenterX-mDecoDeviderLineHalfLength, weatherCenterBaseY,
                            mCenterX+mDecoDeviderLineHalfLength, weatherCenterBaseY, mCenterLine);
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
            float spaceLen = weatherY20thUnit;//12f;

            SunshineWatchFaceUtil.TodayData sunshineData = SunshineWatchFaceUtil.fetchSunshineData(getApplicationContext());
            if (sunshineData!=null) {
                weatherImage = sunshineData.getWeatherImage();
                if (weatherImage!=null){
                    Log.d(TAG, "drawSunshineData() inside image REAL W: " + mWidth + " REAL H: " + mHeight);
                    float resolutionFactor = ((float) mWidth)/280f; // My device width goal of 280dp e.g. 480f/ 280f
                    float scale = resolutionFactor * .5f;               // My best icon image goal - half of the Phone icon scaled to the watch res

//                    int w = Math.round(weatherImage.getWidth()*.5f);
                    int w = (int) (weatherImage.getWidth() * scale);
                    h = (int) (weatherImage.getHeight() * scale);

//                    h = Math.round(weatherImage.getHeight()*.5f);
                    Log.d(TAG, "drawSunshineData() inside image NEW w: " + w + " NEW h: " + h);

                    scaledWeatherImage = weatherImage.createScaledBitmap(weatherImage, w, h, false);
                    iLen = scaledWeatherImage.getWidth();
                }
//                Log.d(TAG, "drawSunshineData() OUTside image REAL W: " + realWidth + " REAL H: " + realHeight);

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
                    + mWeatherDataPaintMuted.measureText(lowOnly)
                    - spaceLen*6
                    ;

            float mWeatherDataXoffset_old = (mWidth-allWeatherLen)/2;

            float allWeatherLen2 = iLen
                    + mWeatherDataPaint.measureText(highOnly)
                    + mWeatherDataPaint.measureText(lowOnly)
//                    + spaceLen*6
                    ;


            mWeatherDataXoffset = (mWidth-allWeatherLen2)/2 + spaceLen;

            Log.d(TAG, "iLen: "+ iLen + " highOnly "+mWeatherDataPaint.measureText(highOnly)+" xOffset " + mWeatherDataXoffset);

            if (scaledWeatherImage!=null) {
//                canvas.drawBitmap(scaledWeatherImage, weatherDataX, mWeatherDataY, null);
//                canvas.drawBitmap(scaledWeatherImage, mWeatherDataXoffset, m_yLine3, null);
//                canvas.drawBitmap(scaledWeatherImage, mWeatherDataXoffset, mCenterY+m_ySixteenth, null);

                canvas.drawBitmap(scaledWeatherImage, mWeatherDataXoffset, weatherCenterBaseY+weatherY20thUnit, null);

                mWeatherDataXoffset = mWeatherDataXoffset + spaceLen/2 + scaledWeatherImage.getWidth();
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
                    mWeatherDataXoffset + spaceLen + mWeatherDataPaint.measureText(highOnly)/2,
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
                long delayMs = mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                Log.v(TAG, "sendEmptyMessageDelayed: "+delayMs+" now at: "+timeMs);
            }
        }

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

            /* Used to calculate x offset of the decoration horizontal divider line */
            mDecoDeviderLineHalfLength = width * 0.09f;
        }
    }
/* *
Note to the Reviewer

If I used the latest, I am getting connection error on the wearable data listener.
compile 'com.google.android.gms:play-services-wearable:10.2.0'

SunshineDataListener: Failed to connect to GoogleApiClient.
*/
}
