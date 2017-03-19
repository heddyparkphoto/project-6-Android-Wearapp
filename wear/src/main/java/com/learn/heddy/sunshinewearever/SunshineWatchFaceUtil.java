package com.learn.heddy.sunshinewearever;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Created by hyeryungpark on 2/16/17.
 */

public class SunshineWatchFaceUtil {
    public static final String TAG = "SunshineWatchFaceUtil";
    /* *
     * The path for the {@link DataItem} containing {@link DigitalWatchFaceService} configuration.
     */
    public static final String PATH_SUNSHINE_WALLPAPER = "/sunshinewearever";
    public static final String HIGH_LOW_KEY = "high_low";
    public static final String BITMAP_KEY = "bitmap";

    private static String mHigh;
    private static String mLow;
    private static Bitmap mBitmap;

    private Context mContext;

    public SunshineWatchFaceUtil(Context context){
        super();
        mContext = context;
    }

    public static void setTodayData(String tempratureString, Bitmap wBitmap){
        int idx = 0;
        int len = 0;

        if (tempratureString!=null) {
            len = tempratureString.length();
            if ((idx = tempratureString.indexOf("/")) > -1) {
                mHigh = tempratureString.substring(0, idx);
                if (len > idx) {
                    mLow = tempratureString.substring(idx + 1);
                }
            }
        }
        mBitmap = wBitmap;
    }

    // Instantiate as invoked with saved fields on this class
    public static TodayData fetchSunshineData(Context context){
        return new TodayData(mHigh, mLow, mBitmap);
    }

    // Convenience class that holds WatchFace field values
    public static class TodayData {

        private String high;
        private String low;
        private Bitmap weatherImage;

        public TodayData(){
            super();
        }

        public TodayData(String hi, String lo, Bitmap image){
            this.high = hi;
            this.low = lo;
            this.weatherImage = image;
        }

        public String getHighOnly(){
            if (high!=null){
                high.trim();
            }
            return high;
        }

        public String getLowOnly(){
            if (low!=null){
                low.trim();
            }
            return low;
        }
        public Bitmap getWeatherImage(){
            return weatherImage;
        }
    }
}
